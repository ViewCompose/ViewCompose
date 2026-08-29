package com.viewcompose.navigation.serialization.samples

import com.viewcompose.navigation.core.NavRouteSpec
import com.viewcompose.navigation.serialization.serializableNavRouteSpec
import kotlinx.serialization.Serializable

// DOCS_REGION_START(navigation-kotlinx-serialization-route)
@Serializable
data class ProfileRoute(
    val userId: Long,
    val tab: String = "posts",
)

val ProfileDestination: NavRouteSpec<ProfileRoute> =
    serializableNavRouteSpec(name = "profile")

fun serializableRouteSample(): ProfileRoute {
    val route = ProfileDestination.encode(ProfileRoute(userId = 42L))
    return ProfileDestination.decode(route)
}
// DOCS_REGION_END(navigation-kotlinx-serialization-route)
