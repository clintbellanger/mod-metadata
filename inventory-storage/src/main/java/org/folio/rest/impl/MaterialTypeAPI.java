package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.Mtype;
import org.folio.rest.jaxrs.model.Mtypes;
import org.folio.rest.jaxrs.resource.MaterialTypeResource;
import org.folio.rest.jaxrs.resource.MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author shale
 *
 */
public class MaterialTypeAPI implements MaterialTypeResource {

  public static final String MATERIAL_TYPE_TABLE   = "material_type";

  private static final String LOCATION_PREFIX       = "/material-type/";
  private static final Logger log                 = LoggerFactory.getLogger(MaterialTypeAPI.class);
  private final Messages messages                 = Messages.getInstance();
  private String idFieldName                      = "_id";


  public MaterialTypeAPI(Vertx vertx, String tenantId){
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(MATERIAL_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getMaterialType(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    /**
    * http://host:port/materialtype
    */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(MATERIAL_TYPE_TABLE, Mtype.class,
          new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if(reply.succeeded()){
                  Mtypes mtypes = new Mtypes();
                  @SuppressWarnings("unchecked")
                  List<Mtype> mtype = (List<Mtype>) reply.result()[0];
                  mtypes.setMtypes(mtype);
                  mtypes.setTotalRecords((Integer)reply.result()[1]);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeResponse.withJsonOK(
                    mtypes)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeResponse
                    .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeResponse
                  .withPlainInternalServerError(messages.getMessage(
                    lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if(e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")){
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeResponse
          .withPlainInternalServerError(message)));
      }
    });
  }

  @Validate
  @Override
  public void postMaterialType(String lang, Mtype entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String id = UUID.randomUUID().toString();
        entity.setId(id);

        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
          MATERIAL_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if(reply.succeeded()){
                Object ret = reply.result();
                entity.setId((String) ret);
                OutStream stream = new OutStream();
                stream.setData(entity);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypeResponse.withJsonCreated(
                  LOCATION_PREFIX + ret, stream)));
              }
              else{
                log.error(reply.cause().getMessage(), reply.cause());
                if(isDuplicate(reply.cause().getMessage())){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypeResponse
                    .withPlainBadRequest("Material Type exists...")));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypeResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypeResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostMaterialTypeResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getMaterialTypeByMaterialtypeId(String materialtypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

        Criterion c = new Criterion(
          new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+materialtypeId+"'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(MATERIAL_TYPE_TABLE, Mtype.class, c, true,
            reply -> {
              try {
                if(reply.succeeded()){
                  @SuppressWarnings("unchecked")
                  List<Mtype> userGroup = (List<Mtype>) reply.result()[0];
                  if(userGroup.isEmpty()){
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeByMaterialtypeIdResponse
                      .withPlainNotFound(materialtypeId)));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeByMaterialtypeIdResponse
                      .withJsonOK(userGroup.get(0))));
                  }
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  if(isInvalidUUID(reply.cause().getMessage())){
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeByMaterialtypeIdResponse
                      .withPlainNotFound(materialtypeId)));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeByMaterialtypeIdResponse
                      .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeByMaterialtypeIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
        });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetMaterialTypeByMaterialtypeIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteMaterialTypeByMaterialtypeId(String materialtypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
      try {
        Mtype mtype = new Mtype();
        mtype.setId(materialtypeId);
        /** check if the material type id exists, if so, get its name / code **/
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(MATERIAL_TYPE_TABLE, mtype, true, false, replyHandler0 -> {
          if(replyHandler0.succeeded()){
            List<Mtype> mtypeList0 = (List<Mtype>) replyHandler0.result()[0];
            if(mtypeList0.size() == 0){
              String message = "Delete of material type with id " + materialtypeId + " failed. id not found";
              log.error(message);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                .withPlainNotFound(message)));
            }
            else{
              String code = mtypeList0.get(0).getName();
              Item item = new Item();
              MaterialType mt = new MaterialType();
              mt.setName(code);
              item.setMaterialType(mt);
              /** check if any item is using this material type **/
              try {
                PostgresClient.getInstance(vertxContext.owner(), tenantId).get(
                  ItemStorageAPI.ITEM_TABLE, item, new String[]{"_id"}, true, false, 0, 1, replyHandler -> {
                  if(replyHandler.succeeded()){
                    List<Item> mtypeList = (List<Item>) replyHandler.result()[0];
                    if(mtypeList.size() > 0){
                      String message = "Can not delete material type, "+ materialtypeId + ". " +
                          mtypeList.size()  + " items associated with it";
                      log.error(message);
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                        .withPlainBadRequest(message)));
                      return;
                    }
                    else{
                      log.info("Attemping delete of unused material type, "+ materialtypeId);
                    }
                    try {
                      PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(MATERIAL_TYPE_TABLE, materialtypeId,
                        reply -> {
                          try {
                            if(reply.succeeded()){
                              if(reply.result().getUpdated() == 1){
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                                  .withNoContent()));
                              }
                              else{
                                log.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                                  .withPlainNotFound(messages.getMessage(lang, MessageConsts.DeletedCountError,1 , reply.result().getUpdated()))));
                              }
                            }
                            else{
                              log.error(reply.cause().getMessage(), reply.cause());
                              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                            }
                          } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                              .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                          }
                        });
                    } catch (Exception e) {
                      log.error(e.getMessage(), e);
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  }
                  else{
                    log.error(replyHandler.cause().getMessage(), replyHandler.cause());
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                      .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            }
          }
        });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putMaterialTypeByMaterialtypeId(String materialtypeId, String lang, Mtype entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
      try {
        if(entity.getId() == null){
          entity.setId(materialtypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
          MATERIAL_TYPE_TABLE, entity, materialtypeId,
          reply -> {
            try {
              if(reply.succeeded()){
                if(reply.result().getUpdated() == 0){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypeByMaterialtypeIdResponse
                    .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypeByMaterialtypeIdResponse
                    .withNoContent()));
                }
              }
              else{
                log.error(reply.cause().getMessage());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypeByMaterialtypeIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypeByMaterialtypeIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutMaterialTypeByMaterialtypeIdResponse
          .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String table, String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(table+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private boolean isDuplicate(String errorMessage){
    if(errorMessage != null && errorMessage.contains("duplicate key value violates unique constraint")){
      return true;
    }
    return false;
  }

  private boolean isInvalidUUID(String errorMessage){
    if(errorMessage != null && errorMessage.contains("invalid input syntax for uuid")){
      return true;
    }
    else{
      return false;
    }
  }

}
