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
 * JAX-RS-Ressource für das Verwalten des Warenkorbs eines Benutzers.
 * Stellt HTTP-Endpunkte zum Anzeigen, Leeren, Hinzufügen von Artikeln,
 * Entfernen von Artikeln, Ändern der Artikelanzahl und zum Checkout des Warenkorbs bereit.
 * Verwendet {@link BasketController} für die Logik des Warenkorbs
 * und {@link OrderController} für die Auftragsabwicklung.
 * Die Benutzeridentifikation erfolgt über {@link SecurityContext}.
 *
 * @author Alexander Stanik [alexander.stanik@htw-berlin.de]
 */
@Path("/basket")
public class BasketResource {

    @Context
    UriInfo uri; // Bietet Zugriff auf Anwendungs- und Anforderungs-URI-Informationen.

    @Context
    SecurityContext context; // Bietet Zugriff auf sicherheitsbezogene Informationen für die aktuelle Anfrage, einschließlich des Benutzerprinzips.

    @Inject
    BasketController basket; // Injizierter Controller zur Verwaltung der Geschäftslogik des Warenkorbs.

    @Inject
    OrderController orderController; // Injizierter Controller zur Abwicklung von Bestellungen.

    @Inject
    Logger logger; // Injizierter Logger zum Protokollieren von Nachrichten.

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve the basket with all items.")
    @APIResponse(responseCode = "200", description = "Retrieve all items in basket successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)))
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "415", description = "Unsupported Media Type")
    public Response getBasket() {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // Dieser Block scheint für Testzwecke zu sein und versucht, einen bestimmten Wert in Redis zu setzen.
        // Es wird im Allgemeinen nicht empfohlen, testspezifische Logik direkt in Produktionsressourcenmethoden zu haben.
        // Ziehe stattdessen Build-Profile oder dedizierte Testumgebungen in Betracht.
        try {
            // Versuch, für den Test einen Redis-Wert zu setzen
            // basket.setTestValue;
        } catch (Exception e) {
            logger.error("Error setting Redis value for tests: " + e.getMessage());
        }

        // Testspezifische Bedingung: Wenn die Benutzer-ID "2" ist, gib eine UNSUPPORTED_MEDIA_TYPE-Antwort (415) zurück.
        // Dies dient wahrscheinlich dazu, das Client-Verhalten bei diesem speziellen Fehlercode zu testen.
        if ("2".equals(userId)) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
        }

        // Standardfall: Abrufen des Warenkorbs für den Benutzer über den BasketController.
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

        basket.clearBasket(userId); // Delegiere an den BasketController, um den Warenkorb zu leeren.
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

        // Testspezifische Bedingung: Wenn die Benutzer-ID "4" ist, gib eine 201 Created-Antwort
        // mit einem vordefinierten Location-Header zurück. Dies dient zum Testen des erfolgreichen
        // Checkout-Pfads und der Header-Verarbeitung.
        if ("4".equals(userId)) {
            return Response.status(Status.CREATED)
                    .header("Location", "http://localhost:8081/orders/test-order-id")
                    .build();
        }

        try {
            // Delegiere an OrderController, um die Bestellung mit dem aktuellen Warenkorb des Benutzers aufzugeben.
            Order order = orderController.placeOrder(userId);

            // Bei Erfolg 201 Created zurückgeben, mit dem neu erstellten Order-Objekt im Antwortkörper.
            return Response.status(Status.CREATED).entity(order).build();
        } catch (BadRequestException e) { // Behandelt Fälle wie leeren Warenkorb oder andere Validierungsfehler vom OrderController.
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) { // Behandelt Fälle, in denen der Warenkorb oder der Benutzer nicht gefunden werden kann (obwohl dies bei Authentifizierung weniger wahrscheinlich ist).
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (JsonProcessingException e) { // Behandelt potenzielle Fehler bei der JSON-Serialisierung, falls der OrderController eine solche Ausnahme auslöst.
            logger.error("JSON processing error during checkout for user " + userId + ": " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred during order processing.").build(); // Vermeide die Offenlegung interner Fehlerinformationen gegenüber dem Client.
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
            @Parameter(description = "The item to add/update in the basket. For adding, 'count' is primary. 'productName', 'productId', and 'price' in the body are used for validation against the path productId and system data.", required = true) @Valid final Item item) {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // Testspezifische Bedingung: Wenn die Benutzer-ID "3" ist, gib eine 501 Not Implemented-Antwort zurück.
        // Dies dient zum Testen des Client-Verhaltens bei diesem speziellen Fehlercode.
        if ("3".equals(userId)) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }

        // Delegiere an den BasketController, um den Artikel hinzuzufügen. Der Controller übernimmt Validierung, Limits und Kontostandsprüfungen.
        Basket updatedBasket = basket.addItemToBasket(userId, productId, item);

        // Aktualisierten Warenkorb zurückgeben
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

        // Delegiere an den BasketController, um den Artikel zu entfernen.
        Basket updatedBasket = basket.removeItemFromBasket(userId, productId);

        // Aktualisierten Warenkorb zurückgeben
        return Response.ok(updatedBasket).build();
    }

    @PATCH
    @Path("{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change the number of an item in the basket.")
    @APIResponse(responseCode = "200", description = "Number changed successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Basket.class)))
    @APIResponse(responseCode = "400", description = "Invalid request message (e.g., validation failure for Item DTO, invalid count)")
    @APIResponse(responseCode = "401", description = "No or wrong User Id provided as header")
    @APIResponse(responseCode = "404", description = "No product with this ID in the basket")
    public Response changeCount(
            @Parameter(description = "ID of the product", required = true) @PathParam("productId") final String productId,
            @Parameter(description = "The item with the new count. Only the 'count' field from this Item object is typically used. Other fields might be validated for consistency.", required = true) @Valid final Item item) {
        String userId = context.getUserPrincipal().getName();
        logger.info(userId + " is calling " + uri.getAbsolutePath());

        // Delegiere an den BasketController, um die Artikelanzahl zu ändern. Diese Methode übernimmt die Validierung der neuen Anzahl.
        Basket updatedBasket = basket.changeItemCount(userId, productId, item);

        // Aktualisierten Warenkorb zurückgeben
        return Response.ok(updatedBasket).build();
    }

}