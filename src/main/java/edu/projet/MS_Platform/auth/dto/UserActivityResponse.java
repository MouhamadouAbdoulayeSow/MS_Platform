// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import java.time.LocalDateTime;

public record UserActivityResponse(
    long connexions,
    LocalDateTime derniereConnexion,
    long coursActifs,
    long quizPasses
) {}
