package com.example.pubsubdemo.controller;

import com.example.pubsubdemo.dto.MessageRequest;
import com.example.pubsubdemo.dto.MessageResponseTinyDTO;
import com.example.pubsubdemo.entity.EventType;
import com.example.pubsubdemo.entity.Message;
import com.example.pubsubdemo.service.MessageService;
import com.example.pubsubdemo.service.PublisherManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageController {

    @Inject
    MessageService messageService;

    @Inject
    PublisherManager publisherManager;

    @GET
    @RolesAllowed({"READER", "PUBLISHER", "ADMIN"})
    public List<Message> getAllMessages() {
        return messageService.getAllMessages();
    }

    @GET
    @Path("/recent")
    @RolesAllowed({"READER", "PUBLISHER", "ADMIN"})
    public List<Message> getRecentMessages() {
        return messageService.getRecentMessages();
    }

    @GET
    @Path("/source/{source}")
    @RolesAllowed({"READER", "PUBLISHER", "ADMIN"})
    public List<Message> getMessagesBySource(@PathParam("source") String source) {
        return messageService.getMessagesBySource(source);
    }

    /**
     * Filtra i messaggi per tipo di evento.
     * Valori accettati: DEMO, USER_EVENT, SYSTEM_EVENT, NOTIFICATION
     */
    @GET
    @Path("/type/{eventType}")
    @RolesAllowed({"READER", "PUBLISHER", "ADMIN"})
    public Response getMessagesByEventType(@PathParam("eventType") String eventTypeStr) {
        try {
            EventType eventType = EventType.valueOf(eventTypeStr.toUpperCase());
            return Response.ok(messageService.getMessagesByEventType(eventType)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Tipo di evento sconosciuto: " + eventTypeStr,
                            "validi", List.of("DEMO", "USER_EVENT", "SYSTEM_EVENT", "NOTIFICATION")))
                    .build();
        }
    }

    /**
     * Restituisce tutti i messaggi finiti in Dead Letter Queue
     * (non elaborati dopo maxDeliveryAttempts tentativi).
     */
    @GET
    @Path("/dlq")
    @RolesAllowed({"ADMIN"})
    public List<Message> getDlqMessages() {
        return messageService.getDlqMessages();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"READER", "PUBLISHER", "ADMIN"})
    public Response getMessageById(@PathParam("id") Long id) {
        try {
            return Response.ok(messageService.getMessageById(id)).build();
        } catch (RuntimeException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @RolesAllowed({"PUBLISHER", "ADMIN"})
    public Response createMessage(@Valid MessageRequest request) {
        Message saved = messageService.saveMessage(request);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    /**
     * Pubblica un messaggio sul topic corrispondente all'eventType nella request.
     * Se eventType è omesso, viene usato DEMO.
     */
    @POST
    @Path("/publish")
    @RolesAllowed({"PUBLISHER", "ADMIN"})
    public Response publishMessage(@Valid MessageRequest request) {
        String messageId = publisherManager.publish(
                request.eventType(),
                request.text(),
                Map.of("source", request.source()));
        return Response.ok(new MessageResponseTinyDTO(messageId, "published")).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"ADMIN"})
    public Response deleteMessage(@PathParam("id") Long id) {
        try {
            messageService.deleteMessage(id);
            return Response.noContent().build();
        } catch (RuntimeException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
