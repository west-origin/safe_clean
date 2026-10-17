package kr.ansimclean.api.job.dto;

import java.util.List;
import java.util.UUID;

/** 고객 실시간 체크리스트와 다시보기 구간. offsetSeconds는 정확한 기준 시각이 없으면 null. */
public record CustomerChecklistResponse(int done, int total, int percent, List<Group> groups,
                                        List<Mark> timeline, List<String> requests) {
    public record Group(String group, int done, int total, List<Item> items) {}
    public record Item(UUID id, String label, boolean done) {}
    public record Mark(UUID id, String group, String label, Long offsetSeconds) {}
}
