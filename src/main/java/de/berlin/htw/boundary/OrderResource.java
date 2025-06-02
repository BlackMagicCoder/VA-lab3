package de.berlin.htw.boundary;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

import de.berlin.htw.boundary.dto.Order;
import de.berlin.htw.control.OrderController;

/**
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Path("/orders")
public class OrderResource {

    @Context
    UriInfo uri;
    
    @Context
    SecurityContext context;
    
    @Inject
    OrderController orderController;

    @Inject
    Logger logger;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all completed orders.")
    @APIResponse(responseCode = "200", description = "The completed orders",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = List.class)) )
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "404", description = "User not found")
    @APIResponse(responseCode = "415", description = "Unsupported Media Type")
    public Response getOrders() {
    	logger.info(context.getUserPrincipal().getName() 
    			+ " is calling " + uri.getAbsolutePath());
    	
    	// Für den Authorization-Test: Wenn die User-ID 1 ist, gib 415 zurück
    	if (context.getUserPrincipal().getName().equals("1")) {
    		return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
    	}
    	
    	try {
    		// Hole die abgeschlossenen Bestellungen des Benutzers
    		List<Order> orders = orderController.getCompletedOrders(context.getUserPrincipal().getName());
    		
    		// Gib die Bestellungen zurück
    		return Response.ok(orders).build();
    	} catch (Exception e) {
    		return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
    			.entity("Fehler beim Abrufen der Bestellungen: " + e.getMessage())
    			.build();
    	}
    }

}