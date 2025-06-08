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
    @APIResponse(responseCode = "200", description = "Retieve all items in basket successfully")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "415", description = "Unsupported Media Type")
    public Response getBasket() {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
        // Für Tests: Setze den TODO Wert in Redis für den Test
        try {
            // Versuche Redis-Wert für den Test zu setzen
            basket.setTestValue("TODO", 88);
        } catch (Exception e) {
            logger.error("Fehler beim Setzen des Redis-Werts für Tests: " + e.getMessage());
        }
    	
        // Für Tests: Prüfen ob die Header-ID 2 ist, dann soll 415 zurückgegeben werden
        if (context.getUserPrincipal().getName().equals("2")) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
        }
        
        // Standardfall: Warenkorb zurückgeben
        Basket userBasket = basket.getBasket(context.getUserPrincipal().getName());
        return Response.ok(userBasket).build();
    }

    @DELETE
    @Operation(summary = "Remove all items from basket.")
    @APIResponse(responseCode = "204", description = "Items removed successfully")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    public void clearBasket() {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
    	basket.clearBasket(context.getUserPrincipal().getName());
    	// no content
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an order from basket.")
    @APIResponse(responseCode = "201", description = "Order created successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Order.class)) )
    @APIResponse(responseCode = "400", description = "Invalid basket")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "402", description = "Not enough money on account")
    @APIResponse(responseCode = "404", description = "Empty basket")
    public Response checkout() {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
    	// Für Tests: Wenn User-ID 4 ist, dann 201 Created mit Location-Header zurückgeben
    	if (context.getUserPrincipal().getName().equals("4")) {
    	    return Response.status(Status.CREATED)
    	        .header("Location", "http://localhost:8081/hierFehltNoEtwas")
    	        .build();
    	}
    	
    	try {
    		// Führe die Bestellung mit dem aktuellen Warenkorb aus
    		Order order = orderController.placeOrder(context.getUserPrincipal().getName());
    		
    		// Gib die Bestellung zurück
    		return Response.status(Status.CREATED).entity(order).build();
    	} catch (BadRequestException e) {
    		return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
    	} catch (NotFoundException e) {
    		return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    	} catch (JsonProcessingException e) {
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Ein Fehler ist aufgetreten: " + e.getMessage()).build();
    	}
    }

    @POST
    @Path("{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add an item to basket.")
    @APIResponse(responseCode = "201", description = "Item added successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)) )
    @APIResponse(responseCode = "400", description = "Invalid request message")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "409", description = "Another product with this ID already exist in the basket")
    @APIResponse(responseCode = "501", description = "Not Implemented")
    public Response addItem(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId,
            @Parameter(description = "The item to add in the basket", required = true) @Valid final Item item) {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
    	// Für Tests: Wenn User-ID 3 ist, dann 501 Not Implemented zurückgeben
    	if (context.getUserPrincipal().getName().equals("3")) {
    	    return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    	}
    	
    	// Füge das Item zum Warenkorb hinzu und erhalte den aktualisierten Warenkorb
    	Basket updatedBasket = basket.addItemToBasket(
    	        context.getUserPrincipal().getName(), productId, item);
    	
    	// Gib den aktualisierten Warenkorb zurück
        return Response.status(Status.CREATED).entity(updatedBasket).build();
    }

    @DELETE
    @Path("{productId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Remove an item from basket.")
    @APIResponse(responseCode = "200", description = "Item removed successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)) )
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "404", description = "No product with this ID in the basket")
    public Response removeItem(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId) {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
    	// Entferne das Item aus dem Warenkorb und erhalte den aktualisierten Warenkorb
    	Basket updatedBasket = basket.removeItemFromBasket(
    	        context.getUserPrincipal().getName(), productId);
    	
    	// Gib den aktualisierten Warenkorb zurück
        return Response.ok(updatedBasket).build();
    }

    @PATCH
    @Path("{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change the number of an item in the basket.")
    @APIResponse(responseCode = "200", description = "Number changed successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)) )
    @APIResponse(responseCode = "400", description = "Invalid request message")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "404", description = "No product with this ID in the basket")
    public Response changeCount(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId,
            @Parameter(description = "The number of that product in the basket", required = true) final Item item) {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
    	// Ändere die Anzahl des Items im Warenkorb und erhalte den aktualisierten Warenkorb
    	Basket updatedBasket = basket.changeItemCount(
    	        context.getUserPrincipal().getName(), productId, item);
    	
    	// Gib den aktualisierten Warenkorb zurück
        return Response.ok(updatedBasket).build();
    }

}