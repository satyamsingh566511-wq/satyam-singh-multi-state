package com.uptimecrew.multistate.readmodel;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data Mongo repository over the {@link TenantReadModel} document.
 *
 * {@code findByPrimaryState} is a derived query backed by the {@code @Indexed}
 * field on the document, so the secondary lookup hits an index rather than a
 * collection scan.
 */
public interface TenantReadModelRepository extends MongoRepository<TenantReadModel, String> {

    List<TenantReadModel> findByPrimaryState(String primaryState);

    /* Derived query: every tenant whose tags array contains the given tag. */
    List<TenantReadModel> findByTagsContaining(String tag);
}
