package com.example.flow;

import com.example.state.IOUState;
import com.example.state.TodoState;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.TransactionVerificationException;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetwork.BasketOfNodes;
import net.corda.testing.node.MockNetwork.MockNode;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;

public class TodoFlowTests {
    private MockNetwork net;
    private MockNode a;
    private MockNode b;

    @Before
    public void setup() {
        net = new MockNetwork();
        BasketOfNodes nodes = net.createSomeNodes(2);
        a = nodes.getPartyNodes().get(0);
        b = nodes.getPartyNodes().get(1);
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        for (MockNode node: nodes.getPartyNodes()) {
            node.registerInitiatedFlow(ExampleFlow.Acceptor.class);
            node.registerInitiatedFlow(TodoCreateFlow.Acceptor.class);
            node.registerInitiatedFlow(TodoCompleteFlow.Acceptor.class);
        }
        net.runNetwork();
    }

    @After
    public void tearDown() {
        net.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void flowRecordsATransactionInBothPartiesVaults() throws Exception {
        TodoCreateFlow.Initiator flow = new TodoCreateFlow.Initiator("example title","example description",b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (MockNode node : ImmutableList.of(a, b)) {
            assertEquals(signedTx, node.storage.getValidatedTransactions().getTransaction(signedTx.getId()));
        }
    }
    @Test
    public void flowCompleteReturnsCompletedFlow() throws Exception {
        TodoCreateFlow.Initiator flow = new TodoCreateFlow.Initiator("example title","example description",b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();
        SignedTransaction signedTx = future.get();

        List<TransactionState<ContractState>> states = signedTx.getTx().getOutputs();
        TransactionState<ContractState> stateTransactionState = states.get(0);
        TodoState output=(TodoState)stateTransactionState.component1();

        assert !(output.getTodoItem().isComplete());
        TodoCompleteFlow.Initiator flowComplete = new TodoCompleteFlow.Initiator(output.getLinearId());
        ListenableFuture<SignedTransaction> futureComplete = b.getServices().startFlow(flowComplete).getResultFuture();
        net.runNetwork();

        SignedTransaction completeTx = futureComplete.get();
        states = completeTx.getTx().getOutputs();
        stateTransactionState = states.get(0);
        TodoState outputComplete=(TodoState)stateTransactionState.component1();

        assert outputComplete.getTodoItem().isComplete();
        // We check the recorded transaction in both vaults.
        for (MockNode node : ImmutableList.of(a, b)) {
            assertEquals(completeTx, node.storage.getValidatedTransactions().getTransaction(completeTx.getId()));
        }
    }

/*
    @Test
    public void flowRejectsInvalidIOUs() throws Exception {
        // The IOUContract specifies that IOUs cannot have negative values.
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(-1, b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();

        // The IOUContract specifies that IOUs cannot have negative values.
        exception.expectCause(instanceOf(TransactionVerificationException.class));
        future.get();
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheInitiator() throws Exception {
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(1, b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();

        SignedTransaction signedTx = future.get();
        signedTx.verifySignatures(b.getServices().getLegalIdentityKey());
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheAcceptor() throws Exception {
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(1, b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();

        SignedTransaction signedTx = future.get();
        signedTx.verifySignatures(a.getServices().getLegalIdentityKey());
    }

    @Test
    public void flowRecordsATransactionInBothPartiesVaults() throws Exception {
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(1, b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (MockNode node : ImmutableList.of(a, b)) {
            assertEquals(signedTx, node.storage.getValidatedTransactions().getTransaction(signedTx.getId()));
        }
    }

    @Test
    public void recordedTransactionHasNoInputsAndASingleOutputTheInputIOU() throws Exception {
        int iouValue = 1;
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(iouValue, b.info.getLegalIdentity());
        ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
        net.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (MockNode node : ImmutableList.of(a, b)) {
            SignedTransaction recordedTx = node.storage.getValidatedTransactions().getTransaction(signedTx.getId());
            List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assert(txOutputs.size() == 1);

            IOUState recordedState = (IOUState) txOutputs.get(0).getData();
            assertEquals(recordedState.getIOU().getValue(), iouValue);
            assertEquals(recordedState.getSender(), a.info.getLegalIdentity());
            assertEquals(recordedState.getRecipient(), b.info.getLegalIdentity());
        }
    }
*/
}