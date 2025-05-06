package com.plcoding.audiorecorder.api;

import com.plcoding.audiorecorder.data.Recording;
import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RecordingApiService {

    @GET("recordings/")
    Call<List<RecordingDto>> getRecordings(
            @Query("device_id") String deviceId
    );

    @GET("recordings/{id}/")
    Call<RecordingDto> getRecording(
            @Path("id") long id
    );

    @Multipart
    @POST("recordings/")
    Call<RecordingDto> uploadVoiceRecording(
            @Part("title") RequestBody title,
            @Part("duration") RequestBody duration,
            @Part("device_id") RequestBody deviceId,
            @Part("type") RequestBody type,  // Change this to RequestBody
            @Part MultipartBody.Part file
    );

    @POST("recordings/")
    Call<RecordingDto> createTextRecording(
            @Body RecordingDto recording
    );

    @DELETE("recordings/{id}/")
    Call<Void> deleteRecording(
            @Path("id") long id
    );

    @GET("recordings/{id}/download/")
    Call<ResponseBody> downloadRecording(
            @Path("id") long id
    );
}