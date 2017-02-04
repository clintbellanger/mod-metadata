package org.folio.inventory.resources

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.BodyHandler
import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.Item
import org.folio.inventory.storage.Storage
import org.folio.metadata.common.WaitForAllFutures
import org.folio.metadata.common.WebContext
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.api.request.VertxBodyParser
import org.folio.metadata.common.api.response.ClientErrorResponse
import org.folio.metadata.common.api.response.JsonResponse
import org.folio.metadata.common.api.response.RedirectResponse
import org.folio.metadata.common.api.response.SuccessResponse

class Items {

  private final Storage storage

  Items(final Storage storage) {
    this.storage = storage
  }

  public void register(Router router) {
    router.post(relativeItemsPath() + "*").handler(BodyHandler.create())

    router.get(relativeItemsPath()).handler(this.&getAll)
    router.post(relativeItemsPath()).handler(this.&create)
    router.delete(relativeItemsPath()).handler(this.&deleteAll)

    router.get(relativeItemsPath() + "/:id").handler(this.&getById)
  }

  void getAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    def limit = context.getIntegerParameter("limit", 10)
    def offset = context.getIntegerParameter("offset", 0)
    def search = context.getStringParameter("query", null)

    if(search == null) {
      storage.getItemCollection(context).findAll(
        new PagingParameters(limit, offset), {

        def waitForAllInstances = new WaitForAllFutures<Instance>()

        it.each { item ->
          storage.getInstanceCollection(context).findById(item.instanceId,
            waitForAllInstances.notifyComplete())
        }

        waitForAllInstances.thenAccept({ instances ->
          JsonResponse.success(routingContext.response(),
            toRepresentation(it, instances, context))
        })
      })
    }
    else {
      storage.getItemCollection(context).findByCql(search,
        new PagingParameters(limit, offset), {
          def waitForAllInstances = new WaitForAllFutures<Instance>()

          it.each { item ->
            storage.getInstanceCollection(context).findById(item.instanceId,
              waitForAllInstances.notifyComplete())
          }

          waitForAllInstances.thenAccept({ instances ->
            println("Got all instances")
            JsonResponse.success(routingContext.response(),
              toRepresentation(it, instances, context))
          })
      })
    }
  }

  void deleteAll(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).empty {
      SuccessResponse.noContent(routingContext.response())
    }
  }

  void create(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    Map itemRequest = new VertxBodyParser().toMap(routingContext)

    def newItem = new Item(itemRequest.title,
      itemRequest.barcode, itemRequest.instanceId)

    storage.getItemCollection(context).add(newItem, {
      RedirectResponse.created(routingContext.response(),
        context.absoluteUrl("${relativeItemsPath()}/${it.id}").toString())
    })
  }

  void getById(RoutingContext routingContext) {
    def context = new WebContext(routingContext)

    storage.getItemCollection(context).findById(
      routingContext.request().getParam("id"),
      {
        if(it != null) {
          storage.getInstanceCollection(context).findById(it.instanceId,
            { instance -> JsonResponse.success(routingContext.response(),
              toRepresentation(it, instance, context))
            }
          )
        }
        else {
          ClientErrorResponse.notFound(routingContext.response())
        }
      })
  }

  private static String relativeItemsPath() {
    "/inventory/items"
  }

  private JsonObject toRepresentation(List<Item> items,
                                      List<Instances> instances,
                                      WebContext context) {

    def representation = new JsonObject()

    def results = new JsonArray()

    items.each { item ->
      results.add(toRepresentation(item,
        instances.find({ it.id == item.instanceId }), context))
    }

    representation.put("items", results)

    representation
  }

  private JsonObject toRepresentation(Item item, Instance instance,
                                      WebContext context) {

    def representation = new JsonObject()
    representation.put("id", item.id)
    representation.put("instanceId", item.instanceId)
    representation.put("title", item.title)
    representation.put("barcode", item.barcode)
    representation.put("publicationDate", instance.publicationDate)

    representation.put('links',
      ['self': context.absoluteUrl(
        relativeItemsPath() + "/${item.id}").toString()])

    representation
  }
}
