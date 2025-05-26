package com.plcoding.audiorecorder.api;

import com.plcoding.audiorecorder.forms.ChecklistQuestionsResponse;
import com.plcoding.audiorecorder.forms.ChecklistSubmissionRequest;
import com.plcoding.audiorecorder.forms.SubmitChecklistResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface TaskChecklistApiService {

    // Remove the ../ path traversal - use absolute paths from the base URL
    @GET("devices/{device_id}/checklists/")
    Call<ChecklistResponse> getAvailableChecklists(@Path("device_id") String deviceId);

    @GET("checklists/{form_id}/questions/")
    Call<ChecklistQuestionsResponse> getChecklistQuestions(@Path("form_id") int formId);

    @POST("devices/{device_id}/submit-checklist/")
    Call<SubmitChecklistResponse> submitChecklist(@Path("device_id") String deviceId, @Body ChecklistSubmissionRequest request);

    @GET("categories/")
    Call<CategoriesResponse> getCategories();

    @GET("submissions/{submission_id}/")
    Call<SubmissionDetailResponse> getSubmissionDetail(@Path("submission_id") int submissionId);
}