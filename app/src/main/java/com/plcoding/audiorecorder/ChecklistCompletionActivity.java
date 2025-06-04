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
import androidx.appcompat.app.AlertDialog;
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

    // ✅ NEW: Camera-specific request codes
    private static final int FRONT_CAMERA_REQUEST = 1004;
    private static final int BACK_CAMERA_REQUEST = 1005;

    private Map<Integer, String> cameraUsageTracker = new HashMap<>();
    private String currentQuestionCameraPreference = "back";


    // UI Components
    private LinearLayout questionsContainer;
    private Button submitButton;
    private Button startTaskButton;

    // Form data
    private int formId;
    private String formTitle;
    private boolean isMandatory;
    private boolean showStartAfterCompletion;

    // Questions data
    private List<ChecklistQuestionsResponse.CategorySection> categorizedQuestions = new ArrayList<>();
    private List<ChecklistQuestion> uncategorizedQuestions = new ArrayList<>();

    // Responses tracking
    private Map<Integer, Object> responses = new HashMap<>();
    private Map<Integer, String> photoResponses = new HashMap<>();
    private Map<Integer, String> photoSourceUsed = new HashMap<>(); // ✅ NEW: Track photo sources
    private Map<Integer, View> questionViews = new HashMap<>();

    // Photo capture state
    private String currentPhotoPath;
    private int currentPhotoQuestionId = -1;
    private String currentPhotoSource; // ✅ NEW: Track current photo source

    // API service
    private TaskChecklistApiService apiService;

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
                questionViews.put(question.getId(), questionView);
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
                questionViews.put(question.getId(), questionView);
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

    // ✅ CORRECTION: Méthode pour gérer les questions avec configuration photo manquante
    private void handleMissingPhotoConfiguration(View questionView, ChecklistQuestion question) {
        TextView errorText = new TextView(this);
        errorText.setText("❌ Photo upload configuration is missing");
        errorText.setTextColor(getColor(android.R.color.holo_red_dark));
        errorText.setPadding(16, 8, 16, 8);
        errorText.setTextSize(14);

        if (questionView instanceof LinearLayout) {
            ((LinearLayout) questionView).addView(errorText);
        }

        Log.e(TAG, "Missing photo configuration for question: " + question.getId() + " - " + question.getText());
    }

    // ✅ CORRECTION: Validation supplémentaire dans createQuestionView
    private View createQuestionView(ChecklistQuestion question) {
        if (question == null) {
            Log.e(TAG, "Cannot create view for null question");
            return new View(this); // Vue vide en cas d'urgence
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View questionView = null;

        String questionType = question.getType();
        if (questionType == null) {
            Log.e(TAG, "Question type is null for question: " + question.getId());
            questionType = ChecklistQuestion.TYPE_TEXT; // Défaut sécurisé
        }

        switch (questionType) {
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
                // ✅ VALIDATION: Vérifier la configuration photo avant de créer la vue
                if (isPhotoSourceValid(question)) {
                    questionView = createPhotoUploadQuestion(question, inflater);
                } else {
                    // Créer une vue d'erreur au lieu de crasher
                    questionView = createErrorQuestionView(question, inflater, "Photo upload configuration missing");
                }
                break;
            default:
                Log.w(TAG, "Unknown question type: " + questionType + " for question: " + question.getId());
                questionView = createTextQuestion(question, inflater); // Défaut sécurisé
                break;
        }

        return questionView;
    }
    // ✅ NOUVELLE MÉTHODE: Créer une vue d'erreur
    private View createErrorQuestionView(ChecklistQuestion question, LayoutInflater inflater, String errorMessage) {
        View view = inflater.inflate(R.layout.question_text_enhanced, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        EditText textInput = view.findViewById(R.id.text_input);

        setupQuestionText(questionText, question);

        // Désactiver l'input et afficher l'erreur
        textInput.setEnabled(false);
        textInput.setText(errorMessage);
        textInput.setTextColor(getColor(android.R.color.holo_red_dark));

        return view;
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

    // ✅ ENHANCED: Create photo upload question with source control
    private View createPhotoUploadQuestion(ChecklistQuestion question, LayoutInflater inflater) {
        View view = inflater.inflate(R.layout.question_photo_enhanced, questionsContainer, false);

        TextView questionText = view.findViewById(R.id.question_text);
        ImageView photoPreview = view.findViewById(R.id.photo_preview);
        Button cameraButton = view.findViewById(R.id.camera_button);
        Button galleryButton = view.findViewById(R.id.gallery_button);
        Button removePhotoButton = view.findViewById(R.id.remove_photo_button);
        TextView fileInfoText = view.findViewById(R.id.file_info_text);
        TextView cameraInfoText = view.findViewById(R.id.camera_info_text);

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

        // ✅ CORRECTION: Vérifications null strictes pour éviter NullPointerException
        if (question.getPhoto_source() != null) {
            StringBuilder cameraInfo = new StringBuilder();

            // ✅ CORRECTION: Vérifications null pour camera_preference
            String cameraPreference = question.getPhoto_source().getCamera_preference();
            String cameraInstructions = question.getPhoto_source().getCamera_instructions();

            if (cameraInstructions != null && !cameraInstructions.trim().isEmpty()) {
                cameraInfo.append("📸 ").append(cameraInstructions);

                // ✅ CORRECTION: Utiliser if-else au lieu de switch pour éviter hashCode() sur null
                if (cameraPreference != null) {
                    if ("front".equals(cameraPreference)) {
                        cameraInfo.append(" 👤");
                    } else if ("back".equals(cameraPreference)) {
                        cameraInfo.append(" 📷");
                    } else if ("any".equals(cameraPreference)) {
                        cameraInfo.append(" 🔄");
                    }
                }

                cameraInfoText.setText(cameraInfo.toString());
                cameraInfoText.setVisibility(View.VISIBLE);
            } else {
                // Si pas d'instructions, cacher le texte
                cameraInfoText.setVisibility(View.GONE);
            }

            // ✅ CORRECTION: Bouton caméra avec gestion des valeurs null
            cameraButton.setOnClickListener(v -> {
                // ✅ Défaut sécurisé pour camera preference
                String safeCameraPreference = cameraPreference != null ? cameraPreference : "back";
                currentQuestionCameraPreference = safeCameraPreference;

                Log.d(TAG, "Camera button clicked - preference: " + safeCameraPreference);
                openCameraWithPreference(question.getId(), safeCameraPreference);
            });

            // ✅ CORRECTION: Configuration des boutons avec vérifications null
            boolean cameraEnabled = question.getPhoto_source().isCamera_enabled();
            boolean galleryEnabled = question.getPhoto_source().isGallery_enabled();

            cameraButton.setVisibility(cameraEnabled ? View.VISIBLE : View.GONE);
            galleryButton.setVisibility(galleryEnabled ? View.VISIBLE : View.GONE);

            // ✅ CORRECTION: Mise à jour du texte du bouton avec vérifications
            if (cameraEnabled && cameraPreference != null) {
                String buttonText = getCameraButtonText(cameraPreference);
                cameraButton.setText(buttonText);
            } else if (cameraEnabled) {
                cameraButton.setText("📸 Camera");
            }
        } else {
            // ✅ GESTION: Si photo_source est null, désactiver les boutons
            Log.w(TAG, "Photo source is null for question: " + question.getId());
            cameraButton.setVisibility(View.GONE);
            galleryButton.setVisibility(View.GONE);
            cameraInfoText.setVisibility(View.GONE);

            // Afficher un message d'erreur
            cameraInfoText.setText("❌ Photo upload configuration missing");
            cameraInfoText.setVisibility(View.VISIBLE);
        }

        // Configuration du bouton gallery
        galleryButton.setOnClickListener(v -> openGallery(question.getId()));

        // Configuration du bouton remove
        removePhotoButton.setOnClickListener(v -> {
            photoPreview.setImageResource(R.drawable.ic_add_photo);
            photoPreview.setScaleType(ImageView.ScaleType.CENTER);
            removePhotoButton.setVisibility(View.GONE);
            photoResponses.remove(question.getId());
            responses.remove(question.getId());
            cameraUsageTracker.remove(question.getId());
            updateFormValidation();
        });

        return view;
    }

    // ✅ NOUVELLE MÉTHODE: Helper pour obtenir le texte du bouton caméra
    private String getCameraButtonText(String cameraPreference) {
        if (cameraPreference == null) {
            return "📸 Camera";
        }

        switch (cameraPreference) {
            case "front":
                return "📱 Selfie Camera";
            case "back":
                return "📷 Main Camera";
            case "any":
                return "📸 Camera";
            default:
                return "📸 Camera";
        }
    }


    private void openCameraWithPreference(int questionId, String cameraPreference) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            currentPhotoQuestionId = questionId;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        // ✅ CORRECTION: Valeur par défaut si cameraPreference est null
        String safePreference = cameraPreference != null ? cameraPreference : "back";

        Log.d(TAG, "Opening camera with preference: " + safePreference + " for question: " + questionId);

        if ("any".equals(safePreference)) {
            showCameraChoiceDialog(questionId);
        } else {
            openSpecificCamera(questionId, safePreference);
        }
    }

    // ✅ CORRECTION: Validation des objets photo source
    private boolean isPhotoSourceValid(ChecklistQuestion question) {
        if (question == null) {
            Log.e(TAG, "Question is null");
            return false;
        }

        if (question.getPhoto_source() == null) {
            Log.e(TAG, "Photo source is null for question: " + question.getId());
            return false;
        }

        return true;
    }


    // ✅ NEW: Show camera choice dialog
    private void showCameraChoiceDialog(int questionId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Camera")
                .setMessage("Which camera would you like to use?")
                .setPositiveButton("📷 Main Camera", (dialog, which) -> {
                    openSpecificCamera(questionId, "back");
                })
                .setNegativeButton("👤 Selfie Camera", (dialog, which) -> {
                    openSpecificCamera(questionId, "front");
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    // ✅ NEW: Open specific camera (front or back)
    private void openSpecificCamera(int questionId, String cameraType) {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoQuestionId = questionId;

                // ✅ Store which camera type was requested
                cameraUsageTracker.put(questionId, cameraType);

                Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // ✅ Set camera facing based on preference
                if ("front".equals(cameraType)) {
                    cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
                    cameraIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
                    cameraIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                    startActivityForResult(cameraIntent, FRONT_CAMERA_REQUEST);
                } else {
                    cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK);
                    cameraIntent.putExtra("android.intent.extras.LENS_FACING_BACK", 0);
                    startActivityForResult(cameraIntent, BACK_CAMERA_REQUEST);
                }

                Log.d(TAG, "Opening " + cameraType + " camera for question " + questionId);
            }
        }
    }

    private void openGallery(int questionId) {
        currentPhotoQuestionId = questionId;
        // ✅ Track that gallery was used (not camera)
        cameraUsageTracker.put(questionId, "gallery");

        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    // ✅ NEW: Handle case where no photo source is available
    private void showNoPhotoSourceAvailable(View questionView, ChecklistQuestion question) {
        TextView errorText = new TextView(this);
        errorText.setText("❌ Photo upload not available for this question");
        errorText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        errorText.setPadding(16, 8, 16, 8);
        errorText.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        // Add to the question view
        if (questionView instanceof LinearLayout) {
            ((LinearLayout) questionView).addView(errorText, 1); // Add after question text
        }

        Log.w(TAG, "No photo source available for question: " + question.getText());
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

    // ✅ ENHANCED: Open camera with proper question context
    private void openCameraForQuestion(ChecklistQuestion question) {
        if (!ValidationHelper.validatePhotoSource(question, "camera")) {
            Toast.makeText(this, "Camera not available for this question", Toast.LENGTH_SHORT).show();
            return;
        }

        currentPhotoQuestionId = question.getId();
        currentPhotoSource = "camera";

        Log.d(TAG, "Opening camera for question: " + question.getText() + " (ID: " + question.getId() + ")");

        openCamera(question.getId(), "camera");
    }

    // ✅ ENHANCED: Open gallery with proper question context
    private void openGalleryForQuestion(ChecklistQuestion question) {
        if (!ValidationHelper.validatePhotoSource(question, "gallery")) {
            Toast.makeText(this, "Gallery not available for this question", Toast.LENGTH_SHORT).show();
            return;
        }

        currentPhotoQuestionId = question.getId();
        currentPhotoSource = "gallery";

        Log.d(TAG, "Opening gallery for question: " + question.getText() + " (ID: " + question.getId() + ")");

        openGallery(question.getId(), "gallery");
    }

    private void openCamera(int questionId, String source) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            currentPhotoQuestionId = questionId;
            currentPhotoSource = source;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoQuestionId = questionId;
                currentPhotoSource = source;
                Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    private void openGallery(int questionId, String source) {
        currentPhotoQuestionId = questionId;
        currentPhotoSource = source;
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

    // ✅ ENHANCED: Process photo capture results with full validation
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
        String actualCameraUsed = null;

        try {
            // ✅ ENHANCED: Handle different camera request codes
            if (requestCode == FRONT_CAMERA_REQUEST || requestCode == BACK_CAMERA_REQUEST || requestCode == CAMERA_REQUEST_CODE) {
                imagePath = currentPhotoPath;
                bitmap = BitmapFactory.decodeFile(imagePath);

                // ✅ Determine which camera was actually used
                if (requestCode == FRONT_CAMERA_REQUEST) {
                    actualCameraUsed = "front";
                } else if (requestCode == BACK_CAMERA_REQUEST) {
                    actualCameraUsed = "back";
                } else {
                    // Legacy camera request - use tracked preference
                    actualCameraUsed = cameraUsageTracker.get(currentPhotoQuestionId);
                    if (actualCameraUsed == null || "gallery".equals(actualCameraUsed)) {
                        actualCameraUsed = "back"; // Default assumption
                    }
                }

                // ✅ Update camera usage tracker with actual camera used
                cameraUsageTracker.put(currentPhotoQuestionId, actualCameraUsed);

                Log.d(TAG, "Photo taken with " + actualCameraUsed + " camera for question " + currentPhotoQuestionId);

            } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
                Uri selectedImage = data.getData();
                if (selectedImage != null) {
                    InputStream imageStream = getContentResolver().openInputStream(selectedImage);
                    bitmap = BitmapFactory.decodeStream(imageStream);
                    if (imageStream != null) {
                        imageStream.close();
                    }

                    // ✅ Ensure gallery usage is tracked
                    cameraUsageTracker.put(currentPhotoQuestionId, "gallery");
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

                    // ✅ Log camera configuration compliance
                    String configuredPreference = targetQuestion.getPhoto_source() != null ?
                            targetQuestion.getPhoto_source().getCamera_preference() : "back";
                    String actualUsage = cameraUsageTracker.get(currentPhotoQuestionId);

                    boolean configCompliant = "gallery".equals(actualUsage) ||
                            "any".equals(configuredPreference) ||
                            configuredPreference.equals(actualUsage);

                    Log.d(TAG, "Photo processed successfully for question " + currentPhotoQuestionId);
                    Log.d(TAG, "Configuration compliance - Configured: " + configuredPreference +
                            ", Actual: " + actualUsage + ", Compliant: " + configCompliant);
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

    // ✅ NEW: Reset photo capture state
    private void resetPhotoCapture() {
        currentPhotoQuestionId = -1;
        currentPhotoPath = null;
        currentPhotoSource = null;
    }

    // ✅ NEW: Show photo processing error
    private void showPhotoError(String message) {
        Toast.makeText(this, "❌ " + message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Photo processing error: " + message);
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
                    openCameraWithPreference(currentPhotoQuestionId, currentQuestionCameraPreference);
                }
            } else {
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                currentPhotoQuestionId = -1;
            }
        }
    }


    // ✅ ENHANCED: Form validation with photo source checking
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

    // ✅ NEW: Get all questions from both categorized and uncategorized lists
    private List<ChecklistQuestion> getAllQuestions() {
        List<ChecklistQuestion> allQuestions = new ArrayList<>();

        // Add categorized questions
        for (ChecklistQuestionsResponse.CategorySection section : categorizedQuestions) {
            allQuestions.addAll(section.getQuestions());
        }

        // Add uncategorized questions
        allQuestions.addAll(uncategorizedQuestions);

        return allQuestions;
    }

    // ✅ ENHANCED: Submit checklist with photo source tracking
    private void submitChecklist() {
        List<ChecklistAnswer> responseList = new ArrayList<>();

        for (Map.Entry<Integer, Object> entry : responses.entrySet()) {
            ChecklistAnswer response = new ChecklistAnswer();
            response.setQuestion_id(String.valueOf(entry.getKey()));

            Object value = entry.getValue();

            // Handle photo uploads specially with camera tracking
            if (photoResponses.containsKey(entry.getKey())) {
                String photoData = photoResponses.get(entry.getKey());
                response.setPhoto_base64(photoData);
                response.setValue(photoData);

                // ✅ NEW: Add camera usage information
                String cameraUsage = cameraUsageTracker.get(entry.getKey());
                if (cameraUsage != null) {
                    if ("gallery".equals(cameraUsage)) {
                        response.setPhoto_source_used("gallery");
                    } else if ("front".equals(cameraUsage) || "back".equals(cameraUsage)) {
                        response.setPhoto_source_used("camera");
                        response.setCamera_used(cameraUsage);
                    }
                }

                Log.d(TAG, "Adding photo response for question " + entry.getKey() +
                        " (size: " + (photoData != null ? photoData.length() : 0) + " chars)" +
                        " (source: " + (cameraUsage != null && !"gallery".equals(cameraUsage) ? "camera-" + cameraUsage : "gallery") + ")");
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
        Log.d(TAG, "Camera usage tracking: " + cameraUsageTracker.size() + " entries");

        Call<SubmitChecklistResponse> call = apiService.submitChecklist(deviceId, request);
        call.enqueue(new Callback<SubmitChecklistResponse>() {
            @Override
            public void onResponse(Call<SubmitChecklistResponse> call, Response<SubmitChecklistResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SubmitChecklistResponse submitResponse = response.body();
                    if ("success".equals(submitResponse.getStatus())) {
                        // ✅ Enhanced success message with camera statistics
                        String message = "Checklist submitted successfully";

                        // Add camera usage summary if available
                        if (response.body().getCamera_statistics() != null) {
                            try {
                                org.json.JSONObject cameraStats = new org.json.JSONObject(response.body().getCamera_statistics().toString());
                                int totalPhotos = cameraStats.optInt("total_camera_photos", 0);
                                int frontUsed = cameraStats.optInt("front_camera_used", 0);
                                int backUsed = cameraStats.optInt("back_camera_used", 0);
                                String matchRate = cameraStats.optString("configuration_match_rate", "N/A");

                                if (totalPhotos > 0) {
                                    message += String.format("\n📸 Photos: %d total, 👤 %d front, 📷 %d back\n✅ Config compliance: %s",
                                            totalPhotos, frontUsed, backUsed, matchRate);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error parsing camera statistics", e);
                            }
                        }

                        Toast.makeText(ChecklistCompletionActivity.this, message, Toast.LENGTH_LONG).show();

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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up resources
        if (questionsContainer != null) {
            questionsContainer.removeAllViews();
        }

        // Clear data structures
        responses.clear();
        photoResponses.clear();
        photoSourceUsed.clear();
        questionViews.clear();
        categorizedQuestions.clear();
        uncategorizedQuestions.clear();

        Log.d(TAG, "ChecklistCompletionActivity destroyed and cleaned up");
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save current state if needed
        Log.d(TAG, "Activity paused - Current responses: " + responses.size() +
                ", Photos: " + photoResponses.size() +
                ", Photo sources: " + photoSourceUsed.size());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Restore state if needed
        Log.d(TAG, "Activity resumed");

        // Update form validation in case something changed
        updateFormValidation();
    }

    // ✅ NEW: Debug method to log current form state
    private void logFormState() {
        Log.d(TAG, "=== FORM STATE DEBUG ===");
        Log.d(TAG, "Form ID: " + formId);
        Log.d(TAG, "Form Title: " + formTitle);
        Log.d(TAG, "Is Mandatory: " + isMandatory);
        Log.d(TAG, "Show Start After Completion: " + showStartAfterCompletion);
        Log.d(TAG, "Total Questions: " + getAllQuestions().size());
        Log.d(TAG, "Responses: " + responses.size());
        Log.d(TAG, "Photo Responses: " + photoResponses.size());
        Log.d(TAG, "Photo Sources: " + photoSourceUsed.size());

        // Log photo source details
        for (Map.Entry<Integer, String> entry : photoSourceUsed.entrySet()) {
            ChecklistQuestion question = findQuestionById(entry.getKey());
            String questionText = question != null ? question.getText() : "Unknown";
            Log.d(TAG, "Photo Question " + entry.getKey() + " (" + questionText + "): " + entry.getValue());
        }

        Log.d(TAG, "========================");
    }

    // ✅ NEW: Helper method for debugging - call this when needed
    public void debugFormState() {
        logFormState();
    }
}