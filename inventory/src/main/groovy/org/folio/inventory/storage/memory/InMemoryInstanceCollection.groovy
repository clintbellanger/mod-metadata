package org.folio.inventory.storage.memory

import org.folio.inventory.domain.Instance
import org.folio.inventory.domain.InstanceCollection
import org.folio.metadata.common.api.request.PagingParameters
import org.folio.metadata.common.storage.memory.InMemoryCollection

class InMemoryInstanceCollection
  implements InstanceCollection {

  private final collection = new InMemoryCollection<Instance>()

  @Override
  void add(Instance item, Closure resultCallback) {
    if(item.id == null) {
      collection.add(item.copyWithNewId(UUID.randomUUID().toString()),
        resultCallback)
    }
    else {
      collection.add(item, resultCallback)
    }
  }

  @Override
  void findById(String id, Closure resultCallback) {
    collection.findOne({ it.id == id }, resultCallback)
  }

  @Override
  void findAll(Closure resultCallback) {
    collection.all(resultCallback)
  }

  @Override
  void findAll(PagingParameters pagingParameters, Closure resultCallback) {
    collection.some(pagingParameters, resultCallback)
  }

  @Override
  void empty(Closure completionCallback) {
    collection.empty(completionCallback)
  }

  @Override
  void findByCql(String cqlQuery, PagingParameters pagingParameters,
                Closure resultCallback) {

    collection.find(cqlQuery, pagingParameters, resultCallback)
  }
}
