/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.indexlifecycle;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.Index;

public class DeleteStep extends AsyncActionStep {

    public DeleteStep(StepKey key, StepKey nextStepKey, Client client) {
        super(key, nextStepKey, client);
    }

    @Override
    public void performAction(Index index, Listener listener) {
        getClient().admin().indices()
            .delete(new DeleteIndexRequest(index.getName()),
                ActionListener.wrap(response -> listener.onResponse(true) , listener::onFailure));
    }

    @Override
    public boolean indexSurvives() {
        return false;
    }
}