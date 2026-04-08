package io.dataease.job.schedule;

import io.dataease.utils.LogUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanScheduler {

    @Scheduled(cron = "0 0 0 * * ?")
    public void clean() {
        LogUtil.info("Skip export cleaner in dataset-only mode.");
    }
}
