package kr.ansimclean.api.friend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** POST /customer/friends/requests · /customer/friends/blocks — 대상 고객 id */
public record UserIdRequest(@NotNull UUID userId) {}
