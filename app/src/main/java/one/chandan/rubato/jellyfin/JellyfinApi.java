package one.chandan.rubato.jellyfin;

import one.chandan.rubato.jellyfin.model.JellyfinAuthRequest;
import one.chandan.rubato.jellyfin.model.JellyfinAuthResponse;
import one.chandan.rubato.jellyfin.model.JellyfinItemsResponse;
import one.chandan.rubato.jellyfin.model.JellyfinViewsResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface JellyfinApi {
    @POST("Users/AuthenticateByName")
    Call<JellyfinAuthResponse> authenticate(
            @Body JellyfinAuthRequest request,
            @Header("X-Emby-Authorization") String authorization
    );

    @GET("Users/{userId}/Views")
    Call<JellyfinViewsResponse> getViews(
            @Path("userId") String userId,
            @Header("X-Emby-Token") String token
    );

    @GET("Users/{userId}/Items")
    Call<JellyfinItemsResponse> getItems(
            @Path("userId") String userId,
            @Query("ParentId") String parentId,
            @Query("IncludeItemTypes") String includeItemTypes,
            @Query("Recursive") Boolean recursive,
            @Query("StartIndex") Integer startIndex,
            @Query("Limit") Integer limit,
            @Query("SortBy") String sortBy,
            @Query("SortOrder") String sortOrder,
            @Query("SearchTerm") String searchTerm,
            @Header("X-Emby-Token") String token
    );

    @GET("Playlists/{playlistId}/Items")
    Call<JellyfinItemsResponse> getPlaylistItems(
            @Path("playlistId") String playlistId,
            @Query("UserId") String userId,
            @Query("StartIndex") Integer startIndex,
            @Query("Limit") Integer limit,
            @Header("X-Emby-Token") String token
    );
}
