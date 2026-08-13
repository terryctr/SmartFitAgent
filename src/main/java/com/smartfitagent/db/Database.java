package com.smartfitagent.db;
import com.smartfitagent.model.*; import java.util.*;
public interface Database { void init() throws Exception; String name(); Optional<UserProfile> user(); void saveUser(UserProfile u); List<Course> courses(); void saveCourse(Course c); List<StudyPlan> plans(); void savePlan(StudyPlan p); List<Note> notes(); void saveNote(Note n); List<QuizQuestion> questions(); void saveQuestion(QuizQuestion q); List<QuizAttempt> attempts(); void saveAttempt(QuizAttempt a); }
