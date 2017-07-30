package com.example.state;

import com.example.contract.TodoContract;
import com.example.model.TodoItem;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static net.corda.core.crypto.CryptoUtils.getKeys;

/**
 * Created by varunmathur on 01/07/2017.
 */
public class TodoState implements LinearState{
    private final TodoItem todoItem;
    private final Party owner;
    private final Party assignee;
    private final UniqueIdentifier linearId;
    private final TodoContract todoContract = new TodoContract();

    public TodoState(TodoItem todoItem, Party owner, Party assignee)
    {
        this.todoItem = todoItem;
        this.owner = owner;
        this.assignee = assignee;
        this.linearId = new UniqueIdentifier();
    }

    public TodoItem getTodoItem()
    {
        return todoItem;
    }

    public Party getOwner()
    {
        return owner;
    }

    public Party getAssignee()
    {
        return assignee;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId()
    {
        return linearId;
    }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(owner, assignee);
    }

    @Override public boolean isRelevant(Set<? extends PublicKey> ourKeys) {
        final List<PublicKey> partyKeys = Stream.of(owner, assignee)
            .flatMap(party -> getKeys(party.getOwningKey()).stream())
            .collect(toList());
        return ourKeys
            .stream()
            .anyMatch(partyKeys::contains);

    }
    @Override
    public TodoContract getContract()
    {
        return todoContract;
    }

}
