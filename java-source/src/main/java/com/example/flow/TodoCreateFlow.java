package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.TodoContract;
import com.example.model.TodoItem;
import com.example.state.TodoState;
import com.google.common.collect.Sets;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * Created by varunmathur on 01/07/2017.
 */
public class TodoCreateFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final String title;
        private final String description;
        private final Party assignee;

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

        public Initiator(String title, String description, Party assignee) {
            this.title=title;
            this.description=description;
            this.assignee=assignee;
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
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            TodoItem todo = new TodoItem(title,description,false);
            Party me = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            TodoState todoState = new TodoState(todo, me, assignee);

            final Command txCommand = new Command(new TodoContract.Commands.Create(),
                todoState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary).withItems(new StateAndContract(todoState,"com.example.contract.TodoContract"), txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.toLedgerTransaction(getServiceHub()).verify();

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            FlowSession otherPartyFlow = initiateFlow(assignee);

            final SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(otherPartyFlow),CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class signTxFlow extends SignTransactionFlow {
                private signTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an todo transaction.", output instanceof TodoState);
                        return null;
                    });
                }
            }

            return subFlow(new signTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }
}
