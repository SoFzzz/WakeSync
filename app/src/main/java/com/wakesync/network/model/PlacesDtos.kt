package com.wakesync.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationBiasDto(
    val lat: Double,
    val lng: Double
)

@Serializable
data class AutocompleteRequestDto(
    val query: String,
    val sessionToken: String,
    val bias: LocationBiasDto? = null
)

@Serializable
data class PlacePredictionDto(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String? = null
)

@Serializable
data class AutocompleteResponseDto(
    val predictions: List<PlacePredictionDto> = emptyList()
)

@Serializable
data class PlaceDetailsDto(
    val placeId: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

@Serializable
data class ReverseGeocodeDto(
    val name: String,
    val address: String
)

@Serializable
data class ErrorResponseDto(
    val error: String,
    val message: String
)
