package com.example.contract;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.TransactionForContract;
import net.corda.core.crypto.SecureHash;

/**
 * Created by varunmathur on 01/07/2017.
 */
public class TodoContract implements Contract {
    public void verify(TransactionForContract tx)
    {
        return;
    }
    public interface Commands extends CommandData {
        class Create implements TodoContract.Commands {}
        class Complete implements TodoContract.Commands {}

    }
    /** This is a reference to the underlying legal contract template and associated parameters. */
    private final SecureHash legalContractReference = SecureHash.sha256("Todo contract template and params");
    @Override public final SecureHash getLegalContractReference() { return legalContractReference; }
}
