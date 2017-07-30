package com.example.api;

import com.example.flow.ExampleFlow;
import com.example.flow.TodoCompleteFlow;
import com.example.flow.TodoCreateFlow;
import com.example.state.IOUState;
import com.example.state.TodoState;
import com.google.common.collect.ImmutableMap;
import kotlin.Pair;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.NetworkMapCache;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import org.bouncycastle.asn1.x500.X500Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static net.corda.client.rpc.UtilsKt.notUsed;

/**
 * Created by varunmathur on 02/07/2017.
 */
@Path("todo")
public class TodoApi {
    private final CordaRPCOps services;
    private final X500Name myLegalName;
    private final String notaryName = "CN=Controller,O=R3,OU=corda,L=London,C=UK";

    static private final Logger logger = LoggerFactory.getLogger(ExampleApi.class);

    public TodoApi(CordaRPCOps services) {
        this.services = services;
        this.myLegalName = services.nodeIdentity().getLegalIdentity().getName();
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, X500Name> whoami() { return ImmutableMap.of("me", myLegalName); }

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<X500Name>> getPeers() {
        Pair<List<NodeInfo>, Observable<NetworkMapCache.MapChange>> nodeInfo = services.networkMapUpdates();
        notUsed(nodeInfo.getSecond());
        return ImmutableMap.of(
            "peers",
            nodeInfo.getFirst()
                .stream()
                .map(node -> node.getLegalIdentity().getName())
                .filter(name -> !name.equals(myLegalName) && !(name.toString().equals(notaryName)))
                .collect(toList()));
    }

    @GET
    @Path("todos")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<TodoState>> getTodos() {
        Vault.Page<TodoState> vaultStates = services.vaultQuery(TodoState.class);
        return vaultStates.getStates();
    }

    @GET
    @Path("my-todos")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<TodoState>> getMyTodos() {
        Vault.Page<TodoState> vaultStates = services.vaultQuery(TodoState.class);
        List<StateAndRef<TodoState>> allTodos = vaultStates.getStates();
        List<StateAndRef<TodoState>> filtered = new ArrayList<>();
        for (StateAndRef<TodoState> todo : allTodos){
            TodoState state = todo.getState().getData();
            if(state.getAssignee().getName().equals(myLegalName) && !state.getTodoItem().isComplete())
                filtered.add(todo);
        }
        return filtered;
    }

    @GET
    @Path("my-owned-todos")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<TodoState>> getMyOwnedTodos() {
        Vault.Page<TodoState> vaultStates = services.vaultQuery(TodoState.class);
        List<StateAndRef<TodoState>> allTodos = vaultStates.getStates();
        List<StateAndRef<TodoState>> filtered = new ArrayList<>();
        for (StateAndRef<TodoState> todo : allTodos){
            TodoState state = todo.getState().getData();
            if(state.getOwner().getName().equals(myLegalName))
                filtered.add(todo);
        }
        return filtered;
    }

    @GET
    @Path("my-completed-todos")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<TodoState>> getMyCompletedTodos() {
        Vault.Page<TodoState> vaultStates = services.vaultQuery(TodoState.class);
        List<StateAndRef<TodoState>> allTodos = vaultStates.getStates();
        List<StateAndRef<TodoState>> filtered = new ArrayList<>();
        for (StateAndRef<TodoState> todo : allTodos){
            TodoState state = todo.getState().getData();
            if(state.getAssignee().getName().equals(myLegalName) && state.getTodoItem().isComplete())
                filtered.add(todo);
        }
        return filtered;
    }

    @PUT
    @Path("create")
    public Response createTodo(@QueryParam("title") String title, @QueryParam("description") String description,
                               @QueryParam("assignee") X500Name assignee)
    {
        final Party otherParty = services.partyFromX500Name(assignee);

        if (otherParty == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Response.Status status;
        String msg;
        try {
            FlowProgressHandle<SignedTransaction> flowHandle = services
                .startTrackedFlowDynamic(TodoCreateFlow.Initiator.class, title,description, otherParty);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s\n", evt));

            // The line below blocks and waits for the flow to return.
            final SignedTransaction result = flowHandle
                .getReturnValue()
                .get();

            status = Response.Status.CREATED;
            msg = String.format("Transaction id %s committed to ledger.", result.getId());

        } catch (Throwable ex) {
            status = Response.Status.BAD_REQUEST;
            msg = ex.getMessage();
            logger.error(msg, ex);
        }

        return Response
            .status(status)
            .entity(msg)
            .build();
    }

    @PUT
    @Path("complete")
    public Response completeTodo(@QueryParam("id") String linearId)
    {
        UniqueIdentifier uuid = UniqueIdentifier.Companion.fromString(linearId);
        Response.Status status;
        String msg;
        try {
            FlowProgressHandle<SignedTransaction> flowHandle = services
                .startTrackedFlowDynamic(TodoCompleteFlow.Initiator.class,uuid);
            flowHandle.getProgress().subscribe(evt -> System.out.printf(">> %s\n", evt));

            // The line below blocks and waits for the flow to return.
            final SignedTransaction result = flowHandle
                .getReturnValue()
                .get();

            status = Response.Status.ACCEPTED;
            msg = String.format("Transaction id %s committed to ledger.", result.getId());

        } catch (Throwable ex) {
            status = Response.Status.BAD_REQUEST;
            msg = ex.getMessage();
            logger.error(msg, ex);
        }

        return Response
            .status(status)
            .entity(msg)
            .build();
    }
}
