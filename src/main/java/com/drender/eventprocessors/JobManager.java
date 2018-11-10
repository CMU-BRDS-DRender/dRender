package com.drender.eventprocessors;

import com.drender.utils.HttpUtils;
import com.drender.model.Channels;
import com.drender.model.job.Job;
import com.drender.model.job.JobResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class JobManager extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(JobManager.class);
    private HttpUtils httpUtils;

    @Override
    public void start() throws Exception {
        httpUtils = new HttpUtils(vertx);

        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(Channels.JOB_MANAGER)
                .handler(message -> {
                    Job job = Json.decodeValue(message.body().toString(), Job.class);

                    switch (job.getAction()) {
                        case START:
                            httpUtils.post(job.getInstance().getIp(), "/start", 8080, job, JobResponse.class)
                                    .setHandler(ar -> {
                                        if (ar.succeeded()) {
                                            message.reply(Json.encode(ar.result()));
                                        } else {
                                            JobResponse errorResponse =
                                                    JobResponse.builder()
                                                    .ID(job.getID())
                                                    .projectID(job.getProjectID())
                                                    //.outputURI(job.getOutputURI())
                                                    .message("Could not start job: " + ar.cause())
                                                    .build();
                                            message.fail(500, Json.encode(errorResponse));
                                        }
                                    });
                        default:
                    }
                });
    }
}
