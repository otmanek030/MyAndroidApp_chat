package com.plcoding.audiorecorder;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.plcoding.audiorecorder.api.ChecklistResponse;
import com.plcoding.audiorecorder.api.RetrofitClient;
import com.plcoding.audiorecorder.api.TaskChecklistApiService;
import com.plcoding.audiorecorder.forms.ChecklistAnswer;
import com.plcoding.audiorecorder.forms.ChecklistCategory;
import com.plcoding.audiorecorder.forms.ChecklistOption;
import com.plcoding.audiorecorder.forms.ChecklistQuestion;
import com.plcoding.audiorecorder.forms.ChecklistQuestionsResponse;
import com.plcoding.audiorecorder.forms.ChecklistSubmissionRequest;
import com.plcoding.audiorecorder.forms.PhotoUploadHelper;
import com.plcoding.audiorecorder.forms.SubmitChecklistResponse;
import com.plcoding.audiorecorder.forms.ValidationHelper;
import com.plcoding.audiorecorder.utils.DeviceIdHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChecklistCompletionActivity extends AppCompatActivity {
    private static final String TAG = "ChecklistCompletion";
    private static final int CAMERA_REQUEST_CODE = 1001;
    private static final int GALLERY_REQUEST_CODE = 1002;
    private static final int CAMERA_PERMISSION_REQUEST = 1003;

    private LinearLayout questionsContainer;
    private Button submitButton;
    private Button startTaskButton;

    private int formId;
    private String formTitle;
    private boolean isMandatory;
    private boolean showStartAfterCompletion;

    private List<ChecklistQuestionsResponse.CategorySection> categorizedQuestions = new ArrayList<>();
    private List<ChecklistQuestion> uncategorizedQuestions = new ArrayList<>();
    private Map<Integer, Object> responses = new HashMap<>();
    private Map<Integer, String> photoResponses = new HashMap<>();
    private Map<Integer, View> questionViews = new HashMap<>(); // Track question views

    private TaskChecklistApiService apiService;
    private String currentPhotoPath;
    private int currentPhotoQuestionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checklist_completion);

        // Get intent extras
        formId = getIntent().getIntExtra("form_id", -1);
        formTitle = getIntent().getStringExtra("form_title");
        isMandatory = getIntent().getBooleanExtra("is_mandatory", false);

        apiService = RetrofitClient.getInstance(this).createService(TaskChecklistApiService.class);

        initializeViews();
        loadQuestions();
    }

    private void initializeViews() {
        setTitle(formTitle);

        questionsContainer = findViewById(R.id.questions_container);
        submitButton = findViewById(R.id.submit_button);
        startTaskButton = findViewById(R.id.start_task_button);

        submitButton.setOnClickListener(v -> submitChecklist());
        startTaskButton.setOnClickListener(v -> startTask());

        // Initially hide start task button if needed
        if (isMandatory) {
            startTaskButton.setVisibility(View.GONE);
        }
    }

    private void loadQuestions() {
        Call<ChecklistQuestionsResponse> call = apiService.getChecklistQuestions(formId);
        call.enqueue(new Callback<ChecklistQuestionsResponse>() {
            @Override
            public void onResponse(Call<ChecklistQuestionsResponse> call, Response<ChecklistQuestionsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ChecklistQuestionsResponse questionsResponse = response.body();
                    if ("success".equals(questionsResponse.getStatus())) {
                        categorizedQuestions.clear();
                        uncategorizedQuestions.clear();

                        if (questionsResponse.getCategorized_questions() != null) {
                            categorizedQuestions.addAll(questionsResponse.getCategorized_questions());
                        }
                        if (questionsResponse.getUncategorized_questions() != null) {
                            uncategorizedQuestions.addAll(questionsResponse.getUncategorized_questions());
                        }

                        showStartAfterCompletion = questionsResponse.getForm().isShow_start_button_after_completion();

                        displayQuestions();
                    }
                }
            }

            @Override
            public void onFailure(Call<ChecklistQuestionsResponse> call, Throwable t) {
                Log.e(TAG, "Error loading questions", t);
                Toast.makeText(ChecklistCompletionActivity.this, "Error loading questions", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayQuestions() {
        questionsContainer.removeAllViews();
        questionViews.clear();

        // Add categorized questions
        for (ChecklistQuestionsResponse.CategorySection categorySection : categorizedQuestions) {
            addCategoryHeader(categorySection.getCategory());
            for (ChecklistQuestion question : categorySection.getQuestions()) {
                View questionView = createQuestionView(question);
                questionsContainer.addView(questionView);
                questionViews.put(question.getId(), questionView); // Store view reference
            }
        }

        // Add uncategorized questions
        if (!uncategorizedQuestions.isEmpty()) {
            if (!categorizedQuestions.isEmpty()) {
                addCategoryHeader(null); // "Other Questions" header
            }
            for (ChecklistQuestion question : uncategorizedQuestions) {
                View questionView = createQuestionView(question);
                questionsContainer.addView(questionView);
                questionViews.put(question.getId(), questionView); // Store view reference
            }
        }

        updateFormValidation();
    }

    private void addCategoryHeader(ChecklistCategory category) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View headerView = inflater.inflate(R.layout.category_header, questionsContainer, false);

        TextView categoryTitle = headerView.findViewById(R.id.category_title);
        TextView categoryDescription = headerView.findViewById(R.id.category_description);

        if (category != null) {
            categoryTitle.setText(category.getName());
            if (category.getDescription() != null && !category.getDescription().isEmpty()) {
                categoryDescription.setText(category.getDescription());
                categoryDescription.setVisibility(View.VISIBLE);
            } else {
                categoryDescription.setVisibility(View.GONE);
            }

            // Set category color if available
            if (category.getColor() != null) {
                try {
                    int color = android.graphics.Color.parseColor(category.getColor());
                    headerView.setBackgroundColor(color & 0x1AFFFFFF); // Add transparency
                } catch (Exception e) {
                    Log.w(TAG, "Invalid color: " + category.getColor());
                }
            }
        } else {
            categoryTitle.setText("Other Questions");
            categoryDescription.setVisibility(View.GONE);
        }

        questionsContainer.addView(headerView);
    }

    private View createQuestionView(ChecklistQuestion question) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View questionView = null;

        switch (question.getType()) {
            case ChecklistQuestion.TYPE_YES_NO:
                questionView = createYesNoQuestion(question, inflater);
                break;
            case ChecklistQuestion.TYPE_RADIO_SINGLE:
                questionView = createRadioSingleQuestion(question, inflater);
                break;
            case ChecklistQuestion.TYPE_RADIO_MULTIPLE:
                questionView = createRadioMultipleQuestion(question, inflater);
                break;
            case ChecklistQuestion.TYPE_TEXT:
            case ChecklistQuestion.TYPE_PARAGRAPH:
                questionView = createTextQuestion(question, inflater);
                break;
            case ChecklistQuestion.TYPE_INTEGER:
            case ChecklistQuestion.TYPE_DECIMAL:
                questionView = createNumberQuestion(question, inflater);
                break;
            case ChecklistQuestion.TYPE_DATE:
                questionView = createDateQuestion(question, inflater);
                break;
            case ChecklistQuestion.TYPE_PHOTO_UPLOAD:
                questionView = createPhotoUploadQuestion(question, inflater);
                break;
        }

        return questionView;
    }

    private View createYesNoQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_yes_no, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        RadioGroup radioGroup = view.findViewById(R.id.yes_no_radio_group);

        setupQuestionText(questionText, question);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean response = checkedId == R.id.yes_button;
            responses.put(question.getId(), response);
            updateFormValidation();
        });

        return view;
    }

    private View createRadioSingleQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_radio_single, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        RadioGroup radioGroup = view.findViewById(R.id.options_radio_group);

        setupQuestionText(questionText, question);

        // Add radio buttons for each option
        for (ChecklistOption option : question.getOptions()) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(option.getText());
            radioButton.setId(View.generateViewId());
            radioButton.setTag(option.getId());
            radioButton.setPadding(16, 12, 16, 12);
            radioGroup.addView(radioButton);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedButton = group.findViewById(checkedId);
            if (selectedButton != null) {
                int optionId = (Integer) selectedButton.getTag();
                responses.put(question.getId(), optionId);
                updateFormValidation();
            }
        });

        return view;
    }

    private View createRadioMultipleQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_radio_multiple, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        LinearLayout optionsContainer = view.findViewById(R.id.options_container);

        setupQuestionText(questionText, question);

        List<Integer> selectedOptions = new ArrayList<>();

        // Add checkboxes for each option
        for (ChecklistOption option : question.getOptions()) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(option.getText());
            checkBox.setTag(option.getId());
            checkBox.setPadding(16, 12, 16, 12);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int optionId = (Integer) buttonView.getTag();
                if (isChecked) {
                    if (!selectedOptions.contains(optionId)) {
                        selectedOptions.add(optionId);
                    }
                } else {
                    selectedOptions.remove(Integer.valueOf(optionId));
                }

                responses.put(question.getId(), new ArrayList<>(selectedOptions));
                updateFormValidation();
            });

            optionsContainer.addView(checkBox);
        }

        return view;
    }

    private View createTextQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_text_enhanced, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        EditText editText = view.findViewById(R.id.text_input);
        TextView charCounter = view.findViewById(R.id.char_counter);

        setupQuestionText(questionText, question);

        // Configure for paragraph type
        if (ChecklistQuestion.TYPE_PARAGRAPH.equals(question.getType())) {
            editText.setLines(4);
            editText.setMaxLines(8);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setVerticalScrollBarEnabled(true);
            editText.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        }

        setupHelpText(view, question);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                responses.put(question.getId(), text);

                // Update character counter
                if (charCounter != null) {
                    charCounter.setText(text.length() + " characters");
                }

                updateFormValidation();
            }
        });

        return view;
    }

    private View createNumberQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_number_enhanced, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        EditText editText = view.findViewById(R.id.number_input);
        TextView validationText = view.findViewById(R.id.validation_text);

        setupQuestionText(questionText, question);
        setupHelpText(view, question);

        // Configure input type
        if (ChecklistQuestion.TYPE_INTEGER.equals(question.getType())) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }

        // Set validation text
        if (question.getValidation() != null) {
            StringBuilder validationInfo = new StringBuilder();
            if (question.getValidation().getMin_value() != null && question.getValidation().getMax_value() != null) {
                validationInfo.append("Range: ").append(question.getValidation().getMin_value())
                        .append(" - ").append(question.getValidation().getMax_value());
            } else if (question.getValidation().getMin_value() != null) {
                validationInfo.append("Minimum: ").append(question.getValidation().getMin_value());
            } else if (question.getValidation().getMax_value() != null) {
                validationInfo.append("Maximum: ").append(question.getValidation().getMax_value());
            }

            if (validationInfo.length() > 0) {
                validationText.setText(validationInfo.toString());
                validationText.setVisibility(View.VISIBLE);
            }
        }

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();

                // Validate input
                String errorMessage = ValidationHelper.getValidationErrorMessage(question, text);
                if (errorMessage != null) {
                    editText.setError(errorMessage);
                    responses.put(question.getId(), null);
                } else {
                    editText.setError(null);
                    if (!text.isEmpty()) {
                        try {
                            if (ChecklistQuestion.TYPE_INTEGER.equals(question.getType())) {
                                responses.put(question.getId(), Integer.parseInt(text));
                            } else {
                                responses.put(question.getId(), Double.parseDouble(text));
                            }
                        } catch (NumberFormatException e) {
                            responses.put(question.getId(), null);
                        }
                    } else {
                        responses.put(question.getId(), null);
                    }
                }

                updateFormValidation();
            }
        });

        return view;
    }

    private View createDateQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_date_enhanced, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        EditText dateInput = view.findViewById(R.id.date_input);
        Button calendarButton = view.findViewById(R.id.calendar_button);

        setupQuestionText(questionText, question);
        setupHelpText(view, question);

        dateInput.setInputType(InputType.TYPE_NULL);
        dateInput.setFocusable(false);
        dateInput.setClickable(true);

        View.OnClickListener dateClickListener = v -> showDatePicker(question, dateInput);
        dateInput.setOnClickListener(dateClickListener);
        calendarButton.setOnClickListener(dateClickListener);

        return view;
    }

    private View createPhotoUploadQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_photo_enhanced, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        ImageView photoPreview = view.findViewById(R.id.photo_preview);
        Button cameraButton = view.findViewById(R.id.camera_button);
        Button galleryButton = view.findViewById(R.id.gallery_button);
        Button removePhotoButton = view.findViewById(R.id.remove_photo_button);
        TextView fileInfoText = view.findViewById(R.id.file_info_text);

        setupQuestionText(questionText, question);
        setupHelpText(view, question);

        // Set file size and type info
        if (question.getValidation() != null) {
            StringBuilder fileInfo = new StringBuilder();
            if (question.getValidation().getMax_file_size() != null) {
                long maxSizeMB = question.getValidation().getMax_file_size() / (1024 * 1024);
                fileInfo.append("Max size: ").append(maxSizeMB).append("MB");
            }
            if (question.getValidation().getAllowed_file_types() != null) {
                if (fileInfo.length() > 0) fileInfo.append(" • ");
                fileInfo.append("Types: ").append(String.join(", ", question.getValidation().getAllowed_file_types()));
            }

            if (fileInfo.length() > 0) {
                fileInfoText.setText(fileInfo.toString());
                fileInfoText.setVisibility(View.VISIBLE);
            }
        }

        cameraButton.setOnClickListener(v -> openCamera(question.getId()));
        galleryButton.setOnClickListener(v -> openGallery(question.getId()));
        removePhotoButton.setOnClickListener(v -> {
            photoPreview.setImageResource(R.drawable.ic_add_photo);
            photoPreview.setScaleType(ImageView.ScaleType.CENTER);
            removePhotoButton.setVisibility(View.GONE);
            photoResponses.remove(question.getId());
            responses.remove(question.getId());
            updateFormValidation();
        });

        return view;
    }

    private void setupQuestionText(TextView questionText, ChecklistQuestion question) {
        String displayText = question.getText();
        if (question.isIs_required()) {
            displayText += " *";
        }
        questionText.setText(displayText);
    }

    private void setupHelpText(View view, ChecklistQuestion question) {
        TextView helpText = view.findViewById(R.id.help_text);
        if (helpText != null && question.getHelp_text() != null && !question.getHelp_text().isEmpty()) {
            helpText.setText(question.getHelp_text());
            helpText.setVisibility(View.VISIBLE);
        } else if (helpText != null) {
            helpText.setVisibility(View.GONE);
        }
    }

    private void showDatePicker(ChecklistQuestion question, EditText dateInput) {
        Calendar calendar = Calendar.getInstance();

        // If there's already a selected date, use it as the initial date
        Object currentResponse = responses.get(question.getId());
        if (currentResponse instanceof String) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = sdf.parse((String) currentResponse);
                if (date != null) {
                    calendar.setTime(date);
                }
            } catch (Exception e) {
                // Use current date if parsing fails
                calendar = Calendar.getInstance();
            }
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    String displayDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", month + 1, dayOfMonth, year);

                    dateInput.setText(displayDate);
                    responses.put(question.getId(), selectedDate);
                    updateFormValidation();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    private void openCamera(int questionId) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            currentPhotoQuestionId = questionId;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoQuestionId = questionId;
                Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    private void openGallery(int questionId) {
        currentPhotoQuestionId = questionId;
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "CHECKLIST_" + timeStamp + "_";
            File storageDir = getExternalFilesDir("Pictures");
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs();
            }
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentPhotoPath = image.getAbsolutePath();
            return image;
        } catch (IOException e) {
            Log.e(TAG, "Error creating image file", e);
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || currentPhotoQuestionId == -1) {
            return;
        }

        // Find the corresponding question
        ChecklistQuestion targetQuestion = findQuestionById(currentPhotoQuestionId);
        if (targetQuestion == null) {
            Log.e(TAG, "Question not found for ID: " + currentPhotoQuestionId);
            return;
        }

        String imagePath = null;
        Bitmap bitmap = null;

        try {
            if (requestCode == CAMERA_REQUEST_CODE) {
                imagePath = currentPhotoPath;
                bitmap = BitmapFactory.decodeFile(imagePath);
            } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    InputStream imageStream = getContentResolver().openInputStream(selectedImage);
                    bitmap = BitmapFactory.decodeStream(imageStream);
                    if (imageStream != null) {
                        imageStream.close();
                    }
                }
            }

            if (bitmap != null) {
                // Compress and encode image to base64
                String base64Image = compressAndEncodeImage(bitmap, targetQuestion);
                if (base64Image != null) {
                    photoResponses.put(currentPhotoQuestionId, base64Image);
                    responses.put(currentPhotoQuestionId, base64Image);

                    // Update UI to show photo preview
                    updatePhotoPreview(currentPhotoQuestionId, bitmap);
                    updateFormValidation();

                    Log.d(TAG, "Photo processed successfully for question " + currentPhotoQuestionId);
                } else {
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing photo", e);
            Toast.makeText(this, "Error processing photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        currentPhotoQuestionId = -1;
        currentPhotoPath = null;
    }

    private ChecklistQuestion findQuestionById(int questionId) {
        // Search in categorized questions
        for (ChecklistQuestionsResponse.CategorySection section : categorizedQuestions) {
            for (ChecklistQuestion question : section.getQuestions()) {
                if (question.getId() == questionId) {
                    return question;
                }
            }
        }

        // Search in uncategorized questions
        for (ChecklistQuestion question : uncategorizedQuestions) {
            if (question.getId() == questionId) {
                return question;
            }
        }

        return null;
    }

    private String compressAndEncodeImage(Bitmap bitmap, ChecklistQuestion question) {
        try {
            // Compress bitmap
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Compress to JPEG with 85% quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            // Validate file size
            if (question.getValidation() != null && question.getValidation().getMax_file_size() != null) {
                if (imageBytes.length > question.getValidation().getMax_file_size()) {
                    // Try with lower quality if image is too large
                    outputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
                    imageBytes = outputStream.toByteArray();

                    if (imageBytes.length > question.getValidation().getMax_file_size()) {
                        Log.e(TAG, "Image file size exceeds maximum allowed size even after compression");
                        return null;
                    }
                }
            }

            String base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            String dataUrl = "data:image/jpeg;base64," + base64String;

            Log.d(TAG, "Image compressed and encoded. Size: " + imageBytes.length + " bytes");
            return dataUrl;

        } catch (Exception e) {
            Log.e(TAG, "Error compressing and encoding image", e);
            return null;
        }
    }

    private void updatePhotoPreview(int questionId, Bitmap bitmap) {
        View questionView = questionViews.get(questionId);
        if (questionView != null) {
            ImageView photoPreview = questionView.findViewById(R.id.photo_preview);
            Button removeButton = questionView.findViewById(R.id.remove_photo_button);

            if (photoPreview != null && removeButton != null) {
                photoPreview.setImageBitmap(bitmap);
                photoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                removeButton.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentPhotoQuestionId != -1) {
                    openCamera(currentPhotoQuestionId);
                }
            } else {
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                currentPhotoQuestionId = -1;
            }
        }
    }

    private void updateFormValidation() {
        boolean isValid = true;

        // Check all required questions
        List<ChecklistQuestion> allQuestions = new ArrayList<>();
        for (ChecklistQuestionsResponse.CategorySection section : categorizedQuestions) {
            allQuestions.addAll(section.getQuestions());
        }
        allQuestions.addAll(uncategorizedQuestions);

        for (ChecklistQuestion question : allQuestions) {
            if (question.isIs_required()) {
                Object response = responses.get(question.getId());
                if (response == null ||
                        (response instanceof String && ((String) response).trim().isEmpty()) ||
                        (response instanceof List && ((List<?>) response).isEmpty())) {
                    isValid = false;
                    break;
                }
            }
        }

        if (showStartAfterCompletion && isMandatory) {
            startTaskButton.setVisibility(isValid ? View.VISIBLE : View.GONE);
        }
    }

    private void submitChecklist() {
        List<ChecklistAnswer> responseList = new ArrayList<>();

        for (Map.Entry<Integer, Object> entry : responses.entrySet()) {
            ChecklistAnswer response = new ChecklistAnswer();
            response.setQuestion_id(String.valueOf(entry.getKey()));

            Object value = entry.getValue();

            // Handle photo uploads specially
            if (photoResponses.containsKey(entry.getKey())) {
                String photoData = photoResponses.get(entry.getKey());
                response.setPhoto_base64(photoData);
                response.setValue(photoData);
                Log.d(TAG, "Adding photo response for question " + entry.getKey() +
                        " (size: " + (photoData != null ? photoData.length() : 0) + " chars)");
            } else {
                response.setValue(value);
            }

            responseList.add(response);
        }

        ChecklistSubmissionRequest request = new ChecklistSubmissionRequest();
        request.setForm_id(formId);
        request.setResponses(responseList);

        String deviceId = DeviceIdHelper.getDeviceId(this);

        Log.d(TAG, "Submitting checklist with " + responseList.size() + " responses");
        Log.d(TAG, "Photo responses: " + photoResponses.size());

        Call<SubmitChecklistResponse> call = apiService.submitChecklist(deviceId, request);
        call.enqueue(new Callback<SubmitChecklistResponse>() {
            @Override
            public void onResponse(Call<SubmitChecklistResponse> call, Response<SubmitChecklistResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SubmitChecklistResponse submitResponse = response.body();
                    if ("success".equals(submitResponse.getStatus())) {
                        Toast.makeText(ChecklistCompletionActivity.this, "Checklist submitted successfully", Toast.LENGTH_SHORT).show();

                        if (!showStartAfterCompletion || !isMandatory) {
                            startTask();
                        }
                    } else {
                        Toast.makeText(ChecklistCompletionActivity.this,
                                "Error: " + submitResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(TAG, "Submission failed with response code: " + response.code());
                    Toast.makeText(ChecklistCompletionActivity.this,
                            "Submission failed: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<SubmitChecklistResponse> call, Throwable t) {
                Log.e(TAG, "Error submitting checklist", t);
                Toast.makeText(ChecklistCompletionActivity.this,
                        "Error submitting checklist: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startTask() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}