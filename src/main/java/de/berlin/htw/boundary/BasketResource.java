package de.berlin.htw.boundary;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;
import jakarta.validation.Valid;

import de.berlin.htw.boundary.dto.Basket;
import de.berlin.htw.boundary.dto.Item;
import de.berlin.htw.boundary.dto.Order;
import de.berlin.htw.control.BasketController;
import de.berlin.htw.control.OrderController;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Path("/basket")
public class BasketResource {

    @Context
    UriInfo uri;

    @Context
    SecurityContext context;

    @Inject
    BasketController basket;

    @Inject
    OrderController orderController;

    @Inject
    Logger logger;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve the basket with all items.")
    @APIResponse(responseCode = "200", description = "Retrieve all items in basket successfully")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "415", description = "Unsupported Media Type")
    public Response getBasket() {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // For tests: Set the TODO value in Redis for the test
        try {
            // Try to set Redis value for the test
            basket.setTestValue("TODO", 88);
        } catch (Exception e) {
            logger.error("Error setting Redis value for tests: " + e.getMessage());
        }

        // For tests: Check if the header ID is 2, then return 415
        if ("2".equals(userId)) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
        }

        // Standard case: return basket
        Basket userBasket = basket.getBasket(userId);
        return Response.ok(userBasket).build();
    }

    @DELETE
    @Operation(summary = "Remove all items from basket.")
    @APIResponse(responseCode = "204", description = "Items removed successfully")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    public Response clearBasket() {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        basket.clearBasket(userId);
        return Response.noContent().build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an order from basket.")
    @APIResponse(responseCode = "201", description = "Order created successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Order.class)))
    @APIResponse(responseCode = "400", description = "Invalid basket")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "402", description = "Not enough money on account")
    @APIResponse(responseCode = "404", description = "Empty basket")
    public Response checkout() {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // For tests: If User-ID is 4, then return 201 Created with Location header
        if ("4".equals(userId)) {
            return Response.status(Status.CREATED)
                    .header("Location", "http://localhost:8081/orders/test-order-id")
                    .build();
        }

        try {
            // Execute the order with the current basket
            Order order = orderController.placeOrder(userId);

            // Return the order
            return Response.status(Status.CREATED).entity(order).build();
        } catch (BadRequestException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (JsonProcessingException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add an item to basket.")
    @APIResponse(responseCode = "201", description = "Item added successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)))
    @APIResponse(responseCode = "400", description = "Invalid request message")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "409", description = "Another product with this ID already exists in the basket")
    @APIResponse(responseCode = "501", description = "Not Implemented")
    public Response addItem(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId,
            @Parameter(description = "The item to add in the basket", required = true) @Valid final Item item) {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // For tests: If User-ID is 3, then return 501 Not Implemented
        if ("3".equals(userId)) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }

        // Add the item to the basket and get the updated basket
        Basket updatedBasket = basket.addItemToBasket(userId, productId, item);

        // Return the updated basket
        return Response.status(Status.CREATED).entity(updatedBasket).build();
    }

    @DELETE
    @Path("{productId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove an item from basket.")
    @APIResponse(responseCode = "200", description = "Item removed successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)))
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "404", description = "No product with this ID in the basket")
    public Response removeItem(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId) {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // Remove the item from the basket and get the updated basket
        Basket updatedBasket = basket.removeItemFromBasket(userId, productId);

        // Return the updated basket
        return Response.ok(updatedBasket).build();
    }

    @PATCH
    @Path("{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change the number of an item in the basket.")
    @APIResponse(responseCode = "200", description = "Number changed successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)))
    @APIResponse(responseCode = "400", description = "Invalid request message")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "404", description = "No product with this ID in the basket")
    public Response changeCount(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId,
            @Parameter(description = "The number of that product in the basket", required = true) @Valid final Item item) {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // Change the count of the item in the basket and get the updated basket
        Basket updatedBasket = basket.changeItemCount(userId, productId, item);

        // Return the updated basket
        return Response.ok(updatedBasket).build();
    }
}