package com.example.todowithcouchbase.task.controller;

import com.example.todowithcouchbase.base.AbstractRestControllerTest;
import com.example.todowithcouchbase.common.model.CustomPage;
import com.example.todowithcouchbase.common.model.CustomPaging;
import com.example.todowithcouchbase.common.model.dto.response.CustomPagingResponse;
import com.example.todowithcouchbase.task.exception.TaskNotFoundException;
import com.example.todowithcouchbase.task.exception.TaskWithThisNameAlreadyExistException;
import com.example.todowithcouchbase.task.model.Task;
import com.example.todowithcouchbase.task.model.dto.request.GetTaskByNameRequest;
import com.example.todowithcouchbase.task.model.dto.request.SaveTaskRequest;
import com.example.todowithcouchbase.task.model.dto.request.TaskPagingRequest;
import com.example.todowithcouchbase.task.model.dto.request.UpdateTaskRequest;
import com.example.todowithcouchbase.task.model.dto.response.TaskResponse;
import com.example.todowithcouchbase.task.model.entity.TaskEntity;
import com.example.todowithcouchbase.task.model.mapper.CustomPageTaskToCustomPagingTaskResponseMapper;
import com.example.todowithcouchbase.task.model.mapper.TaskToTaskResponseMapper;
import com.example.todowithcouchbase.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;

/**
 * Integration test class for the {@link TaskController}.
 * This class tests the REST endpoints of the {@link TaskController} to ensure that task-related
 * operations are performed correctly.
 */
class TaskControllerTest extends AbstractRestControllerTest {

    @MockBean
    private TaskService taskService;

    private final TaskToTaskResponseMapper taskToTaskResponseMapper =  TaskToTaskResponseMapper.initialize();

    private final CustomPageTaskToCustomPagingTaskResponseMapper customPageTaskToCustomPagingTaskResponseMapper =
            CustomPageTaskToCustomPagingTaskResponseMapper.initialize();

    @Test
    void givenValidTaskRequestWithAdminCreate_whenCreateTask_thenSuccess() throws Exception{

        // Given
        final SaveTaskRequest request = SaveTaskRequest.builder()
                .name("task-name")
                .build();

        final Task expectedTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .build();

        // When
        Mockito.when(taskService.saveTaskToDatabase(any(SaveTaskRequest.class)))
                .thenReturn(expectedTask);

        // Then
        mockMvc.perform(
                MockMvcRequestBuilders
                        .post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(HttpHeaders.AUTHORIZATION,"Bearer " + mockAdminToken.getAccessToken())

        ).andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response").value(expectedTask.getId()));

        // Verify
        Mockito.verify(taskService,Mockito.times(1))
                .saveTaskToDatabase(any(SaveTaskRequest.class));

    }

    @Test
    void givenValidTaskRequest_WhenWithUserCreate_ThenThrowUnAuthorizeException() throws Exception{

        // Given
        final SaveTaskRequest request = SaveTaskRequest.builder()
                .name("task-name")
                .build();

        // Then
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .post("/api/v1/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        // Verify
        Mockito.verify(taskService, Mockito.never()).saveTaskToDatabase(any(SaveTaskRequest.class));

    }

    @Test
    void givenValidTaskRequest_whenForbiddenThroughUser_thenThrowForbidden() throws Exception {

        // Given
        final SaveTaskRequest request = SaveTaskRequest.builder()
                .name("task-name")
                .build();

        // Then
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .post("/api/v1/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockUserToken.getAccessToken())
                                .content(objectMapper.writeValueAsString(request))
                ).andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        // Verify
        Mockito.verify(taskService, Mockito.never())
                .saveTaskToDatabase(any(SaveTaskRequest.class));

    }

    @Test
    void givenTaskPagingRequest_whenGetTasksFromAdmin_thenReturnCustomPageTask() throws Exception {

        // Given
        TaskPagingRequest pagingRequest = TaskPagingRequest.builder()
                .pagination(
                        CustomPaging.builder()
                                .pageSize(1)
                                .pageNumber(1)
                                .build()
                ).build();

        String taskId = UUID.randomUUID().toString();

        TaskEntity expectedEntity = TaskEntity.builder()
                .id(taskId)
                .name("Test Task")
                .build();

        List<TaskEntity> taskEntities = List.of(expectedEntity);

        Page<TaskEntity> taskEntityPage = new PageImpl<>(taskEntities, PageRequest.of(1, 1), taskEntities.size());

        List<Task> taskDomainModels = taskEntities.stream()
                .map(entity -> new Task(entity.getId(), entity.getName()))
                .collect(Collectors.toList());

        CustomPage<Task> taskPage = CustomPage.of(taskDomainModels, taskEntityPage);

        CustomPagingResponse<TaskResponse> expectedResponse = customPageTaskToCustomPagingTaskResponseMapper.toPagingResponse(taskPage);

        // When
        Mockito.when(taskService.getAllTasks(any(TaskPagingRequest.class))).thenReturn(taskPage);

        // Then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pagingRequest))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.content[0].id").value(expectedResponse.getContent().get(0).getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.content[0].name").value(expectedResponse.getContent().get(0).getName()));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getAllTasks(any(TaskPagingRequest.class));

    }

    @Test
    void givenTaskPagingRequest_whenGetTasksFromUser_thenReturnCustomPageTask() throws Exception {

        // Given
        TaskPagingRequest pagingRequest = TaskPagingRequest.builder()
                .pagination(
                        CustomPaging.builder()
                                .pageSize(1)
                                .pageNumber(1)
                                .build()
                ).build();

        String taskId = UUID.randomUUID().toString();

        TaskEntity expectedEntity = TaskEntity.builder()
                .id(taskId)
                .name("Test Task")
                .build();

        List<TaskEntity> taskEntities = List.of(expectedEntity);

        Page<TaskEntity> taskEntityPage = new PageImpl<>(taskEntities, PageRequest.of(1, 1), taskEntities.size());

        List<Task> taskDomainModels = taskEntities.stream()
                .map(entity -> new Task(entity.getId(), entity.getName()))
                .collect(Collectors.toList());

        CustomPage<Task> taskPage = CustomPage.of(taskDomainModels, taskEntityPage);

        CustomPagingResponse<TaskResponse> expectedResponse = customPageTaskToCustomPagingTaskResponseMapper.toPagingResponse(taskPage);

        // When
        Mockito.when(taskService.getAllTasks(any(TaskPagingRequest.class))).thenReturn(taskPage);

        // Then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pagingRequest))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockUserToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.content[0].id").value(expectedResponse.getContent().get(0).getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.content[0].name").value(expectedResponse.getContent().get(0).getName()));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getAllTasks(any(TaskPagingRequest.class));
    }

    @Test
    void givenTaskPagingRequest_WhenUnauthorized_thenThrowUnauthorized() throws Exception {

        // Given
        TaskPagingRequest pagingRequest = TaskPagingRequest.builder()
                .pagination(
                        CustomPaging.builder()
                                .pageSize(1)
                                .pageNumber(1)
                                .build()
                ).build();

        // Then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pagingRequest)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        // Verify
        Mockito.verify(taskService, Mockito.never()).getAllTasks(any(TaskPagingRequest.class));

    }

    @Test
    void givenValidTaskRequestWithAdmin_whenGetTaskByName_thenSuccess() throws Exception {

        // Given
        final String taskName = "Admin Task";
        final GetTaskByNameRequest request = new GetTaskByNameRequest(taskName);

        final Task mockTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .name(taskName)
                .build();

        final TaskResponse expectedResponse = taskToTaskResponseMapper.map(mockTask);

        // When
        Mockito.when(taskService.getTaskByName(any(GetTaskByNameRequest.class)))
                .thenReturn(mockTask);

        // Then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tasks/getByName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.name").value(expectedResponse.getName()));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getTaskByName(any(GetTaskByNameRequest.class));

    }

    @Test
    void givenValidTaskRequestWithUser_whenGetTaskByName_thenSuccess() throws Exception {

        // Given
        final String taskName = "User Task";
        final GetTaskByNameRequest request = new GetTaskByNameRequest(taskName);

        final Task mockTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .name(taskName)
                .build();

        final TaskResponse expectedResponse = taskToTaskResponseMapper.map(mockTask);

        // When
        Mockito.when(taskService.getTaskByName(any(GetTaskByNameRequest.class)))
                .thenReturn(mockTask);

        // Then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tasks/getByName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockUserToken.getAccessToken())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.name").value(expectedResponse.getName()));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getTaskByName(any(GetTaskByNameRequest.class));

    }

    @Test
    void givenUnauthorizedRequest_whenGetTaskByName_thenReturnUnauthorized() throws Exception {

        // Given
        final String taskName = "Unauthorized Task";
        final GetTaskByNameRequest request = new GetTaskByNameRequest(taskName);

        // Then
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tasks/getByName")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        // Verify
        Mockito.verify(taskService, Mockito.times(0)).getTaskByName(any(GetTaskByNameRequest.class));

    }

    @Test
    void givenExistId_whenGetTaskById_thenReturnCustomResponse() throws Exception{

        //Given
        final String mockTaskId = UUID.randomUUID().toString();

        final String mockTaskName = "Mock Task";

        final Task mockTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .name(mockTaskName)
                .build();

        final TaskResponse expectedResponse = taskToTaskResponseMapper.map(mockTask);

        // When
        Mockito.when(taskService.getTaskById(mockTaskId)).thenReturn(mockTask);

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                         .get("/api/v1/tasks/{id}",mockTaskId)
                         .contentType(MediaType.APPLICATION_JSON)
                         .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.id").value(expectedResponse.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.name").value(expectedResponse.getName()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getTaskById(mockTaskId);

    }

    @Test
    void givenUnExistId_whenGetTaskById_thenReturnCustomResponse() throws Exception{

        // Given
        final String mockTaskId = UUID.randomUUID().toString();

        final String mockTaskName = "Mock Task";

        final Task mockTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .name(mockTaskName)
                .build();

        final TaskResponse expectedResponse = taskToTaskResponseMapper.map(mockTask);

        // When
        Mockito.when(taskService.getTaskById(mockTaskId)).thenReturn(mockTask);

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/tasks/{id}",mockTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.id").value(expectedResponse.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.name").value(expectedResponse.getName()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getTaskById(mockTaskId);

    }

    @Test
    void givenNonExistentId_whenGetTaskByIdWithAdmin_thenThrowTaskNotFoundException() throws Exception {

        // Given
        final String nonExistentTaskId = UUID.randomUUID().toString();
        final String expectedMessage = "Task not found!\n Task not found with ID: " + nonExistentTaskId;

        // When
        Mockito.when(taskService.getTaskById(nonExistentTaskId))
                .thenThrow(new TaskNotFoundException("Task not found with ID: " + nonExistentTaskId));

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/tasks/{id}", nonExistentTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.header").value("NOT EXIST"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(expectedMessage))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(false));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getTaskById(nonExistentTaskId);

    }


    @Test
    void givenNonExistentId_whenGetTaskByIdWithUser_thenThrowTaskNotFoundException() throws Exception {

        // Given
        final String nonExistentTaskId = UUID.randomUUID().toString();
        final String expectedMessage = "Task not found!\n Task not found with ID: " + nonExistentTaskId;

        // When
        Mockito.when(taskService.getTaskById(nonExistentTaskId))
                .thenThrow(new TaskNotFoundException("Task not found with ID: " + nonExistentTaskId));

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/tasks/{id}", nonExistentTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockUserToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(expectedMessage))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(false));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).getTaskById(nonExistentTaskId);

    }

    @Test
    void givenExistTaskId_whenUserUnauthorized_thenReturnUnauthorized() throws Exception {

        // Given
        final String mockTaskId = UUID.randomUUID().toString();
        final String mockTaskName = "Mock Task";

        final Task mockTask = Task.builder()
                .id(mockTaskId)
                .name(mockTaskName)
                .build();

        // When
        Mockito.when(taskService.getTaskById(mockTaskId))
                .thenReturn(mockTask);

        // Then
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tasks/{id}",mockTaskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        // Verify
        Mockito.verify(taskService,Mockito.never()).getTaskById(mockTaskId);

    }

    @Test
    void givenValidTaskUpdate_WithAdminUpdate_whenUpdateTask_thenSuccess() throws Exception{

        // Given
        final String mockId = UUID.randomUUID().toString();

        final UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("task-name")
                .build();

        final Task expectedTask = Task.builder()
                .id(mockId)
                .name(request.getName())
                .build();

        // When
        Mockito.when(taskService.updateTaskById(Mockito.anyString(),Mockito.any(UpdateTaskRequest.class)))
                .thenReturn(expectedTask);

        // Then
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .put("/api/v1/tasks/{id}",mockId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header(HttpHeaders.AUTHORIZATION,"Bearer " + mockAdminToken.getAccessToken())

                ).andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.id").value(expectedTask.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response.name").value(expectedTask.getName()));

        // Verify
        Mockito.verify(taskService,Mockito.times(1))
                .updateTaskById(Mockito.anyString(),any(UpdateTaskRequest.class));

    }

    @Test
    void givenTaskWithDuplicateName_whenUpdateTask_thenThrowTaskWithThisNameAlreadyExistException() throws Exception {

        // Given
        final String mockId = UUID.randomUUID().toString();

        final UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("duplicate-task-name")
                .build();

        // When
        Mockito.when(taskService.updateTaskById(Mockito.anyString(), Mockito.any(UpdateTaskRequest.class)))
                .thenThrow(new TaskWithThisNameAlreadyExistException("With given task name = " + request.getName()));

        // Then
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .put("/api/v1/tasks/{id}", mockId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Task with this name already exist\n With given task name = duplicate-task-name"));

        // Verify
        Mockito.verify(taskService, Mockito.times(1))
                .updateTaskById(Mockito.anyString(), Mockito.any(UpdateTaskRequest.class));

    }

    @Test
    void givenInvalidTaskId_whenUpdateTask_thenThrowNotFoundException() throws Exception {

        // Given
        final String nonExistentTaskId = UUID.randomUUID().toString();

        final UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("task-name")
                .build();

        final String expectedMessage = "Task not found!\n Task not found with ID: " + nonExistentTaskId;

        // When
        Mockito.when(taskService.updateTaskById(Mockito.anyString(), Mockito.any(UpdateTaskRequest.class)))
                .thenThrow(new TaskNotFoundException("Task not found with ID: " + nonExistentTaskId));

        // Then
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .put("/api/v1/tasks/{id}", nonExistentTaskId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken())
                )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(expectedMessage))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(false));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).updateTaskById(Mockito.anyString(), Mockito.any(UpdateTaskRequest.class));

    }


    @Test
    void givenValidUpdateTaskRequest_whenUserUnAuthorized_thenReturnUnauthorized() throws Exception {

        // Given
        final String mockId = UUID.randomUUID().toString();

        final UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("task-name")
                .build();

        // Then
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/tasks/{id}",mockId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(HttpHeaders.AUTHORIZATION,"Bearer " + mockUserToken.getAccessToken())
        ).andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        // Verify
        Mockito.verify(taskService,Mockito.never()).updateTaskById(Mockito.anyString(),Mockito.any());

    }

    @Test
    void givenValidUpdateRequest_whenUserNotAuthenticated_thenThrowUnAuthorize() throws Exception{

        // Given
        final String mockId = UUID.randomUUID().toString();

        final UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("task-name")
                .build();

        // Then
        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/tasks/{id}",mockId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        // Verify
        Mockito.verify(taskService,Mockito.never()).updateTaskById(Mockito.anyString(),Mockito.any());

    }

    @Test
    void givenValidTaskId_whenAdminDeletesTask_thenSuccess() throws Exception {
        // Given
        final String taskId = UUID.randomUUID().toString();

        // When
        Mockito.doNothing().when(taskService).deleteTaskById(taskId);

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/v1/tasks/{id}", taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("OK"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.response").value("Task with id " + taskId + "is deleted"));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).deleteTaskById(taskId);

    }

    @Test
    void givenValidTaskId_whenUserDeletesTask_thenForbidden() throws Exception {

        // Given
        final String taskId = UUID.randomUUID().toString();

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/v1/tasks/{id}", taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockUserToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        // Verify
        Mockito.verify(taskService, Mockito.never()).deleteTaskById(taskId);

    }

    @Test
    void givenValidTaskId_whenNotAuthenticated_thenUnauthorized() throws Exception {

        // Given
        final String taskId = UUID.randomUUID().toString();

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/v1/tasks/{id}", taskId))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        // Verify
        Mockito.verify(taskService, Mockito.never()).deleteTaskById(taskId);

    }

    @Test
    void givenInvalidTaskId_whenAdminDeletesTask_thenTaskNotFoundException() throws Exception {

        // Given
        final String taskId = UUID.randomUUID().toString();
        final String expectedMessage = "Task not found!\n";

        // When
        Mockito.doThrow(new TaskNotFoundException())
                .when(taskService).deleteTaskById(taskId);

        // Then
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/v1/tasks/{id}", taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mockAdminToken.getAccessToken()))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isSuccess").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(expectedMessage));

        // Verify
        Mockito.verify(taskService, Mockito.times(1)).deleteTaskById(taskId);

    }


}
