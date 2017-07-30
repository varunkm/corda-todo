package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.IOUContract;
import com.example.contract.TodoContract;
import com.example.model.TodoItem;
import com.example.state.TodoState;
import com.google.common.collect.ImmutableList;
import jdk.nashorn.internal.ir.annotations.Immutable;
import net.corda.core.contracts.*;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultQueryService;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.flows.CollectSignaturesFlow;
import net.corda.flows.FinalityFlow;
import net.corda.flows.SignTransactionFlow;

import java.util.Arrays;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * Created by varunmathur on 02/07/2017.
 */
public class TodoCompleteFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final UniqueIdentifier linearId;

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        );

        private static final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new IOU.");
        private static final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private static final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private static final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
            @Override public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private static final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        public Initiator(UniqueIdentifier linearId) {
            this.linearId=linearId;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException
        {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryNodes().get(0).getNotaryIdentity();

            // Stage 1.
            VaultQueryService vaultQuerySvc = getServiceHub().getVaultQueryService();
            QueryCriteria.LinearStateQueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria(null,Arrays.asList(linearId));
            Vault.Page<TodoState> results = vaultQuerySvc.queryBy(TodoState.class, criteria);
            StateAndRef<TodoState> inputState = results.getStates().get(0);
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.

            TodoState oldState = inputState.component1().component1();
            TodoItem oldStateItem = oldState.getTodoItem();
            TodoItem newItem = new TodoItem(oldStateItem.getTitle(),oldStateItem.getDescription(),true);
            TodoState newState = new TodoState(newItem,oldState.getOwner(),oldState.getAssignee());

            final Command txCommand = new Command(new TodoContract.Commands.Complete(),
                newState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));
            final TransactionBuilder txBuilder = new TransactionType.General.Builder(notary).withItems(inputState, newState,txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            final SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx)).get(0);
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final Party otherParty;

        public Acceptor(Party otherParty) {
            this.otherParty = otherParty;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class signTxFlow extends SignTransactionFlow {
                private signTxFlow(Party otherParty, ProgressTracker progressTracker) {
                    super(otherParty, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an todo transaction.", output instanceof TodoState);
                        require.using("Output must be complete", ((TodoState)output).getTodoItem().isComplete());
                        return null;
                    });
                }
            }

            return subFlow(new signTxFlow(otherParty, SignTransactionFlow.Companion.tracker()));
        }
    }
}
