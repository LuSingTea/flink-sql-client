package com.github.mxb.flink.sql.cluster.model.run.overview;

import lombok.Data;

import java.util.List;

/**
 * <p>jobs run status info</p>
 *
 * @author moxianbin
 * @since 2020/4/9 16:46
 */
@Data
public class JobsRunStatus {
    private List<JobsInfo> jobs;

    @Data
    public static class JobsInfo {
        private String id;
        private String status;
    }

    public boolean contains(String jobId) {

        for (JobsInfo jobInfo : jobs) {
            if (jobId.equalsIgnoreCase(jobInfo.getId())) {
                return true;
            }
        }

        return false;
    }
}
