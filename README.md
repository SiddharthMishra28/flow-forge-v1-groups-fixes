# Orkestra: E2E Test Automation Orchestrator

**Orkestra** is a powerful, open-source Spring Boot 3.x application designed to orchestrate End-to-End (E2E) Test Automation pipelines. It provides a robust platform to manage, execute, and monitor complex testing workflows, with seamless integration with GitLab pipelines.

### Developers
1. [Siddharth Mishra](mailto:siddharth.mishra@ubs.com)

## 🌟 Key Features

- **GitLab Integration**: Natively trigger and monitor GitLab CI/CD pipelines for test execution.
- **Asynchronous Flow Execution**: Run complex test flows asynchronously, with support for sequential and parallel step execution.
- **Dynamic Data Exchange**: Exchange data between pipeline steps at runtime by parsing and merging `output.env` files.
- **Resilience and Recovery**: Easily replay or resume failed test flows from any specific step, with automatic data ingestion.
- **Centralized Test Data Management**: Manage and version your test data with application name mapping, categorization and descriptions.
- **Simplified Flow Creation**: A single, intuitive API to define and create an entire E2E flow with squash test case integration, including all its steps and associated test data.
- **Real-time Log Streaming**: Monitor your test executions in real-time with live log streaming directly in your browser via WebSockets.
- **Comprehensive REST API**: A full-fledged REST API with detailed OpenAPI/Swagger documentation for easy integration and management.
- **Flexible Database Support**: Out-of-the-box support for H2 (development) and PostgreSQL (production).
- **Smart Scheduling System**: Memory-optimized timer-based execution with configurable delays (days, hours, minutes) using database persistence.
- **Automatic Failure Handling**: Flow execution stops immediately when any step fails, preventing resource waste on subsequent steps.

## 🏗️ System Architecture

Orkestra is built on a modular, scalable architecture designed for high performance and maintainability.

### Core Components

1.  **Application**: Represents a GitLab project, including its access credentials (personal access token). This is the primary entity for which flows are defined.
2.  **TestData**: A flexible key-value store for test data associated with an `Application` and `applicationName`. Each `TestData` entity can be categorized for better organization (e.g., `API_CREDENTIALS`, `USER_PROFILES`).
3.  **FlowStep**: An individual, executable step within a flow. It is linked to an `Application` and defines the GitLab pipeline branch, test tag, test stage, and optional timer configuration for delayed execution. The `testTag` value is injected as a `testTag` environment variable in the GitLab pipeline.
4.  **Flow**: An ordered sequence of `FlowSteps` that represents a complete E2E test scenario with squash test case integration (`squashTestCaseId` and `squashTestCase`).
5.  **FlowGroup**: A collection of `Flow`s that can be executed in parallel, with iteration tracking and revolution counting for execution metrics.
6.  **FlowExecution**: A runtime instance of a `Flow`, capturing its state (`RUNNING`, `PASSED`, `FAILED`, `SCHEDULED`), start/end times, the aggregated runtime variables, and metadata including category (FlowGroup name or "uncategorized"), flow group association, and execution counters.
7.  **PipelineExecution**: Represents the execution of a single GitLab pipeline within a `FlowExecution`, tracking its status, scheduled resume time, and associated logs.
8.  **SchedulingService**: Memory-optimized background service that manages delayed step executions using database persistence instead of active polling.

### Execution Workflow

1.  A user triggers a `Flow` execution via the `/api/flows/{id}/execute` endpoint.
2.  The `FlowExecutionService` creates a `FlowExecution` record and begins processing the `FlowSteps` sequentially.
3.  For each `FlowStep`, the service merges the configured `TestData` (including `applicationName`) to create a set of runtime variables.
4.  **Timer Check**: If the step has an `invokeTimer` configuration, the system calculates the resume time and schedules the step for later execution with `SCHEDULED` status.
5.  **Immediate Execution**: For steps without timers, the `GitLabApiClient` triggers the corresponding GitLab pipeline, passing the runtime variables as environment variables.
6.  The application continuously polls the GitLab API to monitor the pipeline status.
7.  If the pipeline generates an `output.env` file as an artifact, the system downloads, parses, and merges it into the `FlowExecution`'s runtime variables for subsequent steps to use.
8.  **Failure Handling**: If any step fails, the flow execution is immediately marked as `FAILED` and **all subsequent steps are skipped** to prevent resource waste.
9.  **Scheduled Execution**: The `SchedulingService` background process checks every minute for `SCHEDULED` steps that are ready to resume and automatically triggers their execution.
10. Logs are streamed in real-time via WebSockets and can be viewed at `http://localhost:8080/logs.html`.
11. Failed flows can be replayed from the failed step using the replay endpoint, with all runtime variables from successful steps automatically restored.

## 💻 System Requirements

- **Java**: OpenJDK 17 or higher
- **Maven**: 3.6+
- **PostgreSQL** (Optional, for production)

## 🛠️ Getting Started: A Beginner's Guide

This guide will walk you through setting up and running your first E2E test flow with Orkestra.

### 1. Clone and Build

First, clone the repository and build the application using Maven.

```bash
git clone <repository-url>
cd flow-forge-v1
mvn clean install
```

### 2. Run the Application

Run the application using the Spring Boot Maven plugin. By default, it runs with an in-memory H2 database.

```bash
mvn spring-boot:run
```

Once started, you can access the following:
- **Application**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **H2 Console**: http://localhost:8080/h2-console
  - **JDBC URL**: `jdbc:h2:mem:testdb`
  - **Username**: `sa`
  - **Password**: `password`

### 3. Configure Your First Application

Before creating a flow, you need to register your GitLab project with Orkestra.

**Endpoint**: `POST /api/applications`

**Payload**:
```json
{
  "applicationName": "My E2E Project",
  "applicationDescription": "My project for end-to-end testing",
  "gitlabProjectId": "YOUR_GITLAB_PROJECT_ID",
  "personalAccessToken": "YOUR_GITLAB_PAT"
}
```
- `gitlabProjectId`: You can find this on your GitLab project's homepage.
- `personalAccessToken`: A GitLab Personal Access Token with `api` scope.

### 4. Create a Test Flow

Now, let's create a complete flow with two steps. This single API call will define the flow and all its components.

**Endpoint**: `POST /api/flows`

**Payload**:
```json
{
  "squashTestCaseId": 12345,
  "squashTestCase": "Verify user login and dashboard functionality",
  "flowSteps": [
    {
      "applicationId": 1,
      "branch": "main",
      "testTag": "login-tests",
      "testStage": "test",
      "description": "Login functionality tests",
      "squashStepIds": [1, 2, 3],
      "testData": [
        {
          "applicationId": 1,
          "applicationName": "AuthService",
          "category": "LOGIN_CREDENTIALS",
          "description": "Standard user credentials",
          "variables": {
            "USERNAME": "testuser",
            "PASSWORD": "testpass123"
          }
        }
      ],
      "invokeTimer": null
    },
    {
      "applicationId": 2,
      "branch": "main",
      "testTag": "dashboard-tests",
      "testStage": "test",
      "description": "Dashboard functionality tests",
      "squashStepIds": [4, 5, 6],
      "testData": [
        {
          "applicationId": 2,
          "applicationName": "DashboardService",
          "category": "UI_CONFIG",
          "description": "Dashboard configuration data",
          "variables": {
            "THEME": "dark",
            "REFRESH_RATE": "30"
          }
        }
      ],
      "invokeTimer": {
        "minutes": "+10",
        "hours": "+2",
        "days": "+1"
      }
    }
  ]
}
```
This creates a flow linked to Squash TM test case `12345` with two sequential steps. The first step executes immediately with inline test data for login, while the second step will wait for 1 day, 2 hours, and 10 minutes after the first step completes before executing.

### 5. Execute the Flow

Trigger the flow execution using its ID (which was returned in the previous step).

**Endpoint**: `POST /api/flows/{id}/execute`

**Payload**:
You can optionally provide additional runtime variables.
```json
{
  "additionalProp1": "string",
  "additionalProp2": "string"
}
```

### 6. Monitor in Real-time

Open the log streaming page in your browser to see live logs. Replace `{flowExecutionUUID}` with the ID from the execution response.

`http://localhost:8080/logs.html?flowExecutionUUID={flowExecutionUUID}`

## 📖 API Reference & Schemas

### Endpoints

#### Applications (GitLab Application Management API)
- `POST /api/applications`: Create a new application.
- `GET /api/applications/{id}`: Get an application by ID.
- `GET /api/applications`: Get all applications. **Supports pagination & sorting**
- `PUT /api/applications/{id}`: Update an application.
- `DELETE /api/applications/{id}`: Delete an application.

#### Test Data (Test Data Management Operations API)
- `POST /api/test-data`: Create new test data.
- `GET /api/test-data/{id}`: Get test data by ID.
- `GET /api/test-data`: Get all test data. **Supports pagination & sorting**
- `PUT /api/test-data/{id}`: Update test data.
- `DELETE /api/test-data/{id}`: Delete test data.

#### Flows (Flow Management API)
- `POST /api/flows`: **🆕 Enhanced!** Create a new flow referencing existing test data by IDs (no duplication).
- `GET /api/flows/{id}`: Get a flow by ID with full test data objects for UI rendering.
- `GET /api/flows`: **🆕 Enhanced!** Get all flows with default sorting by `updatedAt DESC`. **Supports pagination & sorting**
- `PUT /api/flows/{id}`: **🆕 Enhanced!** Update a flow using test data IDs (prevents duplication).
- `DELETE /api/flows/{id}`: **🆕 Enhanced!** Delete a flow (unlinks test data without deletion, preserving data integrity).
- `POST /api/flows/{id}/execute`: Execute a single flow.
- **🆕 NEW!** `POST /api/flows/execute?trigger={flowId1},{flowId2},{flowId3}`: **Execute multiple flows** with intelligent thread pool management and capacity monitoring.

#### Flow Steps (Individual Flow Step Management API)
- `POST /api/flow-steps`: **🆕 Enhanced!** Create a new flow step referencing test data by IDs. **InvokeScheduler now optional!**
- `GET /api/flow-steps/{id}`: Get a flow step by ID with full test data objects for UI rendering.
- `GET /api/flow-steps`: Get all flow steps. **Supports pagination & sorting**
- `PUT /api/flow-steps/{id}`: **🆕 Enhanced!** Update a flow step using test data IDs. **InvokeScheduler now optional!**
- `DELETE /api/flow-steps/{id}`: **🆕 Enhanced!** Delete a flow step (unlinks test data without deletion, preserving data integrity).

#### Flow Executions (Flow Execution Management API)

##### Single Flow Execution
- `GET /api/flow-executions/{id}`: Get flow execution details.
- `GET /api/flows/{flowId}/executions`: Get flow executions by flow ID. **Supports pagination & sorting**
- `POST /api/flow-executions/{flowExecutionUUID}/replay/{failedFlowStepId}`: Replay a failed flow from a specific step.

##### **🆕 Advanced Flow Execution Search**
- `GET /api/flow-executions/search`: Advanced search for flow executions by various filters including UUID, flow ID, flow group name, flow group ID, iteration, and date range. **Supports pagination & sorting**

##### **🆕 Multiple Flow Execution (Brand New!)**
- **🆕 NEW!** `POST /api/flows/execute?trigger={flowId1},{flowId2},{flowId3}`: **Execute multiple flows simultaneously** with intelligent thread pool management, capacity monitoring, and graceful rejection.
- **🆕 NEW!** `GET /api/flows/executions?triggered={flowId1},{flowId2},{flowId3}&search={term}`: **Query multiple flow executions** with pagination and default sorting by `startTime DESC`. Now supports `search` to match by execution `id` (UUID), `squashTestCaseId`, or `squashTestCase` (partial, case-insensitive).

#### Flow Groups (Flow Group Management API)
- `POST /api/flow-groups`: Create a new flow group.
- `GET /api/flow-groups`: Get all flow groups. **Supports pagination & sorting**
- `GET /api/flow-groups/{id}`: Get flow group by ID.
- `PUT /api/flow-groups/{id}`: Update flow group.
- `DELETE /api/flow-groups/{id}`: Delete flow group.
- `POST /api/flow-groups/{id}/execute`: Execute all flows in group in parallel.
- `GET /api/flow-groups/{id}/executions`: Get executions for a flow group with date filtering.
- `GET /api/flow-groups/details`: Get flow group details with associated flows aggregated by flow group name, with pagination, filters and query parameters.

#### Pipeline Executions (Pipeline Execution Monitoring API)
- `GET /api/flow-executions/{flowExecutionUUID}/pipelines`: Get all pipeline executions for a flow execution. **Supports pagination & sorting**
- `GET /api/flow-executions/{flowExecutionUUID}/pipelines/{pipelineExecutionId}`: Get specific pipeline execution details.
- `GET /api/flow-executions/flows/{flowId}/pipelines`: Get all pipeline executions for a flow.
- `GET /api/flow-executions/flow-steps/{flowStepId}/pipelines`: Get all pipeline executions for a flow step.
- `GET /api/flow-executions/{flowExecutionUUID}/gitlab-pipelines/{gitlabPipelineId}`: Get pipeline execution by GitLab pipeline ID.

## 📄 Pagination and Sorting

All "Get All" endpoints now support configurable pagination and sorting for better performance and user experience:

### Parameters
- `page` (optional): Page number (0-based). Default: 0
- `size` (optional): Page size. Default: 20
- `sortBy` (optional): Field to sort by (e.g., 'id', 'createdAt', 'updatedAt', 'applicationName', etc.)
- `sortDirection` (optional): Sort direction ('ASC' or 'DESC'). Default: 'ASC'

### Backward Compatibility
- **Without parameters**: Returns all records (List<T>) - maintains existing behavior
- **With parameters**: Returns paginated response (Page<T>) with metadata

### Example Usage
```bash
# Get all applications with pagination and sorting
GET /api/applications?page=0&size=10&sortBy=applicationName&sortDirection=ASC

# Get all flows with default sorting (updatedAt DESC) - NEW DEFAULT!
GET /api/flows

# Get all flows with pagination (default sorting applies)
GET /api/flows?page=0&size=20

# Get all flows filtered by squash test case with pagination
GET /api/flows?squashTestCaseId=123&page=0&size=20&sortBy=createdAt&sortDirection=DESC

# Get all flow steps filtered by application with sorting only
GET /api/flow-steps?applicationId=1&sortBy=createdAt&sortDirection=DESC

# Get flow executions with pagination (default: startTime DESC) - NEW DEFAULT!
GET /api/flows/1/executions?page=0&size=10

# Get multiple flow executions (new endpoint!) - NEW!
GET /api/flows/executions?triggered=1,2,3&page=0&size=10
# With search across id, squashTestCaseId, or squashTestCase (partial)
GET /api/flows/executions?search=login
GET /api/flows/executions?triggered=1,2,3&search=12345

# Get pipeline executions with sorting
GET /api/flow-executions/uuid/pipelines?sortBy=startTime&sortDirection=DESC

# Execute multiple flows (new endpoint!) - NEW!
POST /api/flows/execute?trigger=1,2,3,4,5
```

### Response Format
**Paginated Response (Page<T>)**:
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```

#### Analytics
- `GET /api/analytics/execution-stats`: Get execution statistics.
- `GET /api/analytics/duration-stats`: Get duration statistics.
- `GET /api/analytics/failure-analysis`: Get failure analysis data.
- `GET /api/analytics/trends`: Get trend data for executions.

## 🚀 Latest Enhancements (v2.0)

### **🔥 Major Feature Additions**

#### **1. Environment Variable Naming Standardization**

**Problem Solved:** GitLab pipeline environment variables were using inconsistent naming conventions.

**Changes Made:**
- Standardized `testTag` from FlowStep to be injected as lowercase `testTag` environment variable in GitLab pipelines
- Ensures proper case-sensitive variable access in CI/CD scripts

**Impact:** Test tags are now accessible as `$testTag` in GitLab CI/CD pipelines for dynamic test filtering and execution.

#### **2. Application Token Validation Tracking**

**Problem Solved:** No way to track when token validation was last performed on applications.

**Changes Made:**
- Added `tokenValidationLastUpdateDate` field to Application model and DTOs
- TokenValidationScheduler now updates this timestamp after each validation cycle
- Repository enhanced with bulk update method for efficient timestamp management

**Impact:** Applications now track their last token validation timestamp, enabling better monitoring and security compliance.

#### **3. Enhanced Flow Execution Response Details**

**Problem Solved:** Flow execution endpoints returned incomplete nested object information.

**Changes Made:**
- Updated `/api/flows/executions` endpoint to return complete flow execution details with all nested objects
- Fixed missing `squashTestCase` field in flow execution responses
- Enhanced response data for better API consumption

**Impact:** Flow execution queries now provide comprehensive information including complete flow, flowSteps, applications, and pipelineExecutions data.

#### **4. Pipeline Execution Job Tracking**

**Problem Solved:** Pipeline executions lacked direct links to specific GitLab jobs that generate artifacts.

**Changes Made:**
- Added `jobId` and `jobUrl` fields to PipelineExecution model and DTOs
- Enhanced artifact download process to capture and store job information for the job matching `testStage`
- Pipeline executions now include direct links to relevant GitLab jobs

**Impact:** Users can now directly navigate to specific GitLab jobs that generate `output.env` artifacts, improving debugging and monitoring capabilities.

#### **5. Enhanced Flow Management with Test Data References**

**Problem Solved:** Eliminated test data duplication in flow creation/update operations while maintaining UI-friendly responses.

**Changes Made:**
- **POST/PUT Operations:** Now accept `testData` as a list of IDs instead of full objects
- **GET Operations:** Continue to return full `TestDataDto` objects for easy UI rendering
- **DELETE Operations:** Unlink test data instead of deleting, preserving data integrity

**Input Payload (POST/PUT):**
```json
{
  "flowSteps": [
    {
      "applicationId": 1,
      "branch": "main",
      "testTag": "regression",
      "testStage": "development",
      "description": "Login flow step",
      "squashStepIds": [1, 2],
      "testData": [1, 2], // Just IDs - no duplication!
      "invokeScheduler": { // Now optional!
        "type": "scheduled",
        "timer": {"minutes": "30", "hours": "14", "days": "1"}
      }
    }
  ],
  "squashTestCaseId": 12345,
  "squashTestCase": "Login Test Case"
}
```

**Output Response (GET):**
```json
{
  "flowSteps": [
    {
      "applicationId": 1,
      "branch": "main",
      "testData": [ // Full objects for UI rendering
        {
          "dataId": 1,
          "applicationName": "MyApp",
          "category": "login",
          "description": "Login test data",
          "variables": {"username": "test", "password": "pass"}
        }
      ],
      "invokeScheduler": { // Can be null now
        "type": "scheduled",
        "timer": {"minutes": "30", "hours": "14", "days": "1"}
      }
    }
  ]
}
```

#### **2. Optional InvokeScheduler Support**

**Problem Solved:** Not all flow steps require scheduling, making mandatory `invokeScheduler` restrictive.

**Changes Made:**
- Removed `@ValidInvokeScheduler` mandatory validation
- Updated DTOs and models to handle null `invokeScheduler` gracefully
- Service logic now supports optional scheduling configuration

#### **3. Multiple Flow Execution with Thread Pool Management**

**Problem Solved:** No way to execute multiple flows simultaneously with proper resource management.

**New Endpoints:**
- `POST /api/flows/execute?trigger=1,2,3` - Execute multiple flows
- `GET /api/flows/executions?triggered=1,2,3&search={term}` - Query multiple flow executions (supports optional search across id, squashTestCaseId, squashTestCase)

**Thread Pool Management:**
```json
{
  "summary": {
    "total_requested": 5,
    "accepted": 3,
    "rejected": 2
  },
  "accepted": [
    {
      "flowId": 1,
      "executionId": "uuid-1",
      "status": "accepted",
      "message": "Flow execution started"
    }
  ],
  "rejected": [
    {
      "flowId": 4,
      "status": "rejected",
      "reason": "thread_pool_capacity",
      "message": "Thread pool at capacity, flow execution rejected"
    }
  ],
  "thread_pool_status": {
    "active_threads": 18,
    "max_threads": 20,
    "queue_size": 95,
    "available_capacity": 7
  }
}
```

**HTTP Status Codes:**
- `202 Accepted` - All flows accepted for execution
- `503 Service Unavailable` - Some flows rejected due to capacity
- `400 Bad Request` - Invalid flow IDs provided

#### **4. Enhanced Default Sorting**

**Problem Solved:** Flows were not sorted by default, making recent flows hard to find.

**Implementation:**
- `/api/flows` now defaults to `updatedAt DESC` (newest first)
- `/api/flows/executions` defaults to `startTime DESC` (most recent first) and supports optional `search` across execution `id`, `squashTestCaseId`, or `squashTestCase`
- Maintains backward compatibility with existing sorting parameters

### **🔧 Technical Implementation Details**

#### **Thread Pool Configuration:**
```yaml
Flow Execution Pool:
  Core Pool Size: 5 threads
  Max Pool Size: 20 threads  
  Queue Capacity: 100 tasks
  Thread Prefix: "FlowExecution-"
```

#### **Capacity Calculation:**
```java
availableCapacity = (maxThreads - activeThreads) + queueRemainingCapacity
```

#### **Intelligent Rejection Logic:**
- Flows beyond available capacity are gracefully rejected
- Detailed reasons provided for each rejection
- System protected from resource exhaustion
- Real-time thread pool status monitoring

#### **Data Integrity Preservation:**
- DELETE operations now unlink instead of delete test data
- Test data remains available for other flows/steps
- Prevents accidental data loss
- Maintains referential integrity

### **🛡️ Backward Compatibility**

**✅ Zero Breaking Changes:**
- All existing endpoints work unchanged
- Single flow execution preserved: `POST /api/flows/{flowId}/execute`
- Existing payload formats still supported for GET operations
- All pagination and filtering parameters maintained
- Response formats remain consistent

### **📊 Performance & Safety Improvements**

**Resource Management:**
- Intelligent thread pool monitoring prevents system overload
- Graceful degradation under high load
- Detailed capacity reporting for monitoring

**Data Efficiency:**
- Eliminated test data duplication in storage
- Reduced payload sizes for create/update operations
- Maintained rich responses for UI consumption

**Error Handling:**
- Comprehensive error categorization
- Detailed failure reasons and suggestions
- Proper HTTP status code usage

### **🎯 Usage Examples**

#### **Multiple Flow Execution**

**Execute 3 flows simultaneously:**
```bash
POST /api/flows/execute?trigger=1,2,3
```

**Response when thread pool has capacity:**
```json
{
  "summary": {
    "total_requested": 3,
    "accepted": 3,
    "rejected": 0
  },
  "accepted": [
    {
      "flowId": 1,
      "executionId": "550e8400-e29b-41d4-a716-446655440001",
      "status": "accepted",
      "message": "Flow execution started"
    },
    {
      "flowId": 2,
      "executionId": "550e8400-e29b-41d4-a716-446655440002",
      "status": "accepted", 
      "message": "Flow execution started"
    },
    {
      "flowId": 3,
      "executionId": "550e8400-e29b-41d4-a716-446655440003",
      "status": "accepted",
      "message": "Flow execution started"
    }
  ],
  "rejected": [],
  "thread_pool_status": {
    "active_threads": 8,
    "max_threads": 20,
    "queue_size": 5,
    "available_capacity": 107
  }
}
```

**Response when thread pool is at capacity:**
```json
{
  "summary": {
    "total_requested": 5,
    "accepted": 2,
    "rejected": 3
  },
  "accepted": [
    {
      "flowId": 1,
      "executionId": "550e8400-e29b-41d4-a716-446655440001",
      "status": "accepted",
      "message": "Flow execution started"
    }
  ],
  "rejected": [
    {
      "flowId": 3,
      "status": "rejected",
      "reason": "thread_pool_capacity",
      "message": "Thread pool at capacity, flow execution rejected"
    },
    {
      "flowId": 999,
      "status": "rejected",
      "reason": "flow_not_found",
      "message": "Flow not found with ID: 999"
    }
  ],
  "thread_pool_status": {
    "active_threads": 20,
    "max_threads": 20,
    "queue_size": 100,
    "available_capacity": 0
  }
}
```

#### **Query Multiple Flow Executions**

**Get executions for multiple flows with pagination:**
```bash
GET /api/flows/executions?triggered=1,2,3&page=0&size=10&sortBy=startTime&sortDirection=DESC
```

**Response:**
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "flowId": 2,
      "startTime": "2024-01-15T10:30:00",
      "endTime": "2024-01-15T10:35:00",
      "status": "PASSED",
      "runtimeVariables": {"SESSION_TOKEN": "abc123"},
      "createdAt": "2024-01-15T10:30:00"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "flowId": 1,
      "startTime": "2024-01-15T10:25:00",
      "endTime": "2024-01-15T10:32:00",
      "status": "FAILED",
      "runtimeVariables": {},
      "createdAt": "2024-01-15T10:25:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "sorted": true,
      "orders": [{"direction": "DESC", "property": "startTime"}]
    }
  },
  "totalElements": 15,
  "totalPages": 2,
  "first": true,
  "last": false
}
```

#### **Enhanced Flow Creation**

**Create flow with test data references (no duplication):**
```bash
POST /api/flows
```

**Request Body:**
```json
{
  "flowSteps": [
    {
      "applicationId": 1,
      "branch": "main",
      "testTag": "smoke",
      "testStage": "test",
      "description": "Login validation",
      "squashStepIds": [101, 102],
      "testData": [5, 7], // Reference existing test data
      "invokeScheduler": null // Optional - can be omitted
    },
    {
      "applicationId": 2,
      "branch": "develop", 
      "testTag": "regression",
      "testStage": "integration",
      "description": "Data processing",
      "squashStepIds": [201],
      "testData": [8, 9, 10],
      "invokeScheduler": {
        "type": "scheduled",
        "timer": {
          "minutes": "0",
          "hours": "2", 
          "days": "*"
        }
      }
    }
  ],
  "squashTestCaseId": 12345,
  "squashTestCase": "End-to-End User Journey"
}
```

**Response (returns full objects for UI):**
```json
{
  "id": 15,
  "flowSteps": [
    {
      "id": 45,
      "applicationId": 1,
      "branch": "main",
      "testTag": "smoke",
      "testStage": "test",
      "description": "Login validation",
      "squashStepIds": [101, 102],
      "testData": [ // Full objects returned for UI consumption
        {
          "dataId": 5,
          "applicationName": "LoginApp",
          "category": "authentication",
          "description": "Valid user credentials",
          "variables": {
            "username": "testuser",
            "password": "securepass"
          }
        },
        {
          "dataId": 7,
          "applicationName": "LoginApp", 
          "category": "authentication",
          "description": "Admin credentials",
          "variables": {
            "username": "admin",
            "password": "adminpass"
          }
        }
      ],
      "invokeScheduler": null,
      "createdAt": "2024-01-15T11:00:00",
      "updatedAt": "2024-01-15T11:00:00"
    }
  ],
  "squashTestCaseId": 12345,
  "squashTestCase": "End-to-End User Journey",
  "createdAt": "2024-01-15T11:00:00",
  "updatedAt": "2024-01-15T11:00:00"
}
```

### Schemas

#### `CombinedFlowDto` (for `POST /api/flows`)
```json
{
  "squashTestCaseId": 0,
  "squashTestCase": "string",
  "flowSteps": [
    {
      "applicationId": 0,
      "branch": "string",
      "testTag": "string",
      "testStage": "string",
      "description": "string",
      "squashStepIds": [ 0 ],
      "testData": [
        {
          "applicationId": 0,
          "applicationName": "string",
          "category": "string",
          "description": "string",
          "variables": {
            "additionalProp1": "string",
            "additionalProp2": "string"
          }
        }
      ],
      "invokeTimer": {
        "minutes": "+10",
        "hours": "+2",
        "days": "+1"
      }
    }
  ]
}
```

#### `TestDataDto` (for `POST /api/test-data`)
```json
{
  "applicationId": 0,
  "applicationName": "string",
  "category": "string",
  "description": "string",
  "variables": {
    "additionalProp1": "string",
    "additionalProp2": "string"
  }
}
```

## ⏰ Smart Scheduling System

Orkestra includes a sophisticated, memory-optimized scheduling system that allows you to introduce delays between flow steps without consuming system resources.

### How It Works

1. **Scheduler Configuration**: Define scheduling using the `invokeScheduler` object with `type` and `timer` fields:
   - **Delayed Execution**: Use `type: "delayed"` with timer values having "+" prefix (e.g., `"+10"` minutes after previous step completion)
   - **Scheduled Execution**: Use `type: "scheduled"` with absolute timer values (e.g., `"14"` for 2 PM, `"30"` for 30 minutes past the hour)
2. **Database Persistence**: When a step needs to be scheduled/delayed, the system calculates the resume time and stores it in the database with `SCHEDULED` status.
3. **Background Processing**: A configurable background service (default: every 60 seconds) checks for scheduled executions that are ready to resume.
4. **Automatic Resumption**: When the scheduled time arrives, the execution status changes to `IN_PROGRESS` to prevent replay delays and automatically resumes from the next step.

### New Test Data Endpoint

A new endpoint has been added for retrieving test data by application ID:

**GET** `/api/{applicationId}/test-data`
- Retrieves all test data entries for a specific application ID
- **Path Parameter**: `applicationId` (Long) - The ID of the application
- **Response**: List of TestDataDto objects

### Configuration

The pipeline status polling interval can be configured in `application.yml`:

```yaml
scheduling:
  pipeline-status:
    # Pipeline status polling interval in milliseconds (default: 60 seconds)
    polling-interval: ${PIPELINE_STATUS_POLLING_INTERVAL:60000}
```

### Timer Format

For **delayed** type:
```json
{
  "invokeScheduler": {
    "type": "delayed",
    "timer": {
      "minutes": "+10",  // 10 minutes after previous step completion
      "hours": "+2",     // 2 hours after previous step completion  
      "days": "+1"       // 1 day after previous step completion
    }
  }
}
```

For **scheduled** type:
```json
{
  "invokeScheduler": {
    "type": "scheduled",
    "timer": {
      "minutes": "30",   // 30 minutes past the hour
      "hours": "14",     // 2 PM (24-hour format)
      "days": "1"        // 1 day from now
    }
  }
}
```

### Legacy Timer Format (Deprecated)

```json
{
  "invokeTimer": {
    "minutes": "+10",  // Wait 10 additional minutes
    "hours": "+2",     // Wait 2 additional hours  
    "days": "+1"       // Wait 1 additional day
  }
}
```

- All fields are optional (at least one must be specified)
- All values must have the "+" prefix to indicate they are added to the previous step's completion time
- The total delay is calculated as: `previous_step_completion + days + hours + minutes`

### Example Use Cases

- **Batch Processing**: Wait overnight before running data validation steps
- **Rate Limiting**: Introduce delays to avoid overwhelming external systems
- **Business Logic**: Simulate real-world delays (e.g., waiting for payment processing)
- **Load Testing**: Stagger test executions across different time periods

## 🚨 Failure Handling

Orkestra implements intelligent failure handling to prevent resource waste and ensure reliable test execution:

### Automatic Flow Termination

- When any flow step fails, the entire flow execution stops immediately
- Subsequent steps are **NOT executed**, preventing wasted resources
- The flow execution status is marked as `FAILED`
- All accumulated runtime variables are preserved for replay functionality

### Replay Functionality

- Failed flows can be replayed from the failed step onwards
- Runtime variables from successful steps are automatically restored
- Use the replay endpoint: `POST /api/flow-executions/{flowExecutionUUID}/replay/{failedFlowStepId}`

### Scheduled Step Failures

- If a scheduled step fails to resume due to system errors, it's automatically marked as `FAILED`
- The scheduling service includes error handling and logging for troubleshooting
- Failed scheduled executions don't impact other scheduled steps

### **⚡ Migration Guide**

#### **For Existing Users (v1.x → v2.0):**

**✅ No Changes Required:**
- All existing API calls continue to work unchanged
- Existing flow execution workflows remain functional
- Current pagination and sorting parameters preserved
- Response formats maintained for GET operations

**🚀 To Use New Features:**

1. **Enhanced Flow Management:**
   ```bash
   # Old way (still works)
   POST /api/flows
   {
     "flowSteps": [{
       "testData": [{"dataId": 1, "category": "login", ...}] // Full objects
     }]
   }
   
   # New way (recommended)
   POST /api/flows  
   {
     "flowSteps": [{
       "testData": [1, 2] // Just IDs - more efficient
     }]
   }
   ```

2. **Multiple Flow Execution:**
   ```bash
   # New capability
   POST /api/flows/execute?trigger=1,2,3
   GET /api/flows/executions?triggered=1,2,3
   ```

3. **Optional Scheduling:**
   ```bash
   # InvokeScheduler can now be null or omitted
   {
     "flowSteps": [{
       "invokeScheduler": null  // or omit entirely
     }]
   }
   ```

### **📈 Key Benefits Summary**

#### **🎯 For Developers:**
- **Reduced Payload Sizes**: 60-80% smaller create/update requests
- **Better Resource Management**: Thread pool monitoring prevents system overload
- **Enhanced Error Handling**: Detailed failure reasons and capacity status
- **Flexible Scheduling**: Optional invokeScheduler for simpler flows

#### **🎯 For Testers:**
- **Bulk Execution**: Run multiple test flows simultaneously
- **Better Organization**: Newest flows/executions appear first by default
- **Data Integrity**: Test data preserved when flows are deleted
- **Real-time Monitoring**: Live thread pool capacity tracking

#### **🎯 For DevOps:**
- **System Stability**: Intelligent capacity management prevents crashes
- **Resource Visibility**: Real-time thread pool status monitoring
- **Scalability**: Better handling of high-load scenarios
- **Backward Compatibility**: Zero downtime migrations

#### **🎯 For Frontend Teams:**
- **Rich Responses**: GET operations still return full objects for easy rendering
- **Frontend-Friendly URLs**: Clean, intuitive API design
- **Comprehensive Pagination**: Enhanced default sorting behavior
- **Detailed Error Responses**: Better user experience with clear error messages

### **🔮 What's Next?**

Future enhancements planned:
- **Batch Scheduling**: Schedule multiple flows for future execution
- **Flow Dependencies**: Execute flows based on completion of others
- **Advanced Analytics**: Enhanced metrics and reporting
- **Webhook Integration**: Real-time notifications for flow completion
- **Flow Templates**: Reusable flow configurations

## 🚀 Latest Enhancements (v3.0)

### **🔥 Major Feature Additions**

#### **1. FlowGroup Orchestration with Parallel Execution**

**Problem Solved:** No way to execute multiple flows as a group with parallel processing and execution metrics tracking.

**New Features:**
- **FlowGroup Entity**: Collections of flows that can be executed together
- **Parallel Flow Execution**: All flows in a group execute simultaneously
- **Iteration Tracking**: Automatic counting of group executions with reset every 100 iterations
- **Revolution Counting**: Tracks complete cycles of 100 iterations

**New Endpoints:**
- `POST /api/flow-groups` - Create flow groups
- `POST /api/flow-groups/{id}/execute` - Execute all flows in group in parallel
- `GET /api/flow-groups/{id}/executions` - Get executions for a flow group with date filtering

#### **2. Enhanced FlowExecution Metadata**

**Problem Solved:** Flow executions lacked categorization and group association tracking.

**Changes Made:**
- Added `category` field (defaults to "uncategorized" for standalone executions, FlowGroup name for group executions)
- Added `flowGroupId`, `iteration`, `revolutions` fields for group execution tracking
- Enhanced search capabilities across all metadata fields

#### **3. Advanced FlowExecution Search**

**Problem Solved:** Limited ability to search and filter flow executions by multiple criteria.

**New Endpoint:**
- `GET /api/flow-executions/search` - Advanced search with filters for:
  - Execution UUID
  - Flow ID
  - Flow Group ID
  - Iteration number
  - Date range (fromDate/toDate)

**Impact:** Comprehensive querying capabilities for execution analysis and monitoring.

### **🔧 Technical Implementation Details**

#### **FlowGroup Execution Logic:**
```java
// Increment iteration counters
int currentIteration = flowGroup.getCurrentIteration() + 1;
int revolutions = flowGroup.getRevolutions();
if (currentIteration > 100) {
    currentIteration = 1;
    revolutions += 1;
}
```

#### **Parallel Execution:**
- Flows in a group execute concurrently using the existing thread pool
- Each flow maintains sequential step execution within itself
- All flows complete asynchronously with individual status tracking

#### **Database Schema Updates:**
- `flow_groups` table with `current_iteration` and `revolutions` columns
- `flow_executions` table with `category`, `flow_group_id`, `iteration`, `revolutions` columns

### **🛡️ Backward Compatibility**

**✅ Zero Breaking Changes:**
- All existing endpoints work unchanged
- Standalone flow execution preserved
- Existing response formats maintained
- New fields are optional/additional metadata

### **🎯 Usage Examples**

#### **Create and Execute a FlowGroup**
```bash
# Create flow group
POST /api/flow-groups
{
  "flowGroupName": "E2E Test Suite",
  "flows": [1, 2, 3]
}

# Execute all flows in parallel
POST /api/flow-groups/1/execute

# Get executions for the group
GET /api/flow-groups/1/executions?fromDate=2024-01-01T00:00:00&toDate=2024-01-31T23:59:59
```

#### **Advanced FlowExecution Search**
```bash
# Search by multiple criteria
GET /api/flow-executions/search?flowGroupId=1&iteration=5&fromDate=2024-01-01T00:00:00
```

### **📊 Performance & Monitoring Improvements**

**Execution Tracking:**
- Iteration counters provide execution frequency metrics
- Revolution counting enables long-term usage analysis
- Category field enables execution type segmentation

**Resource Management:**
- Parallel execution maximizes throughput for grouped flows
- Individual flow status monitoring maintained
- Thread pool utilization optimized for concurrent flows

## 🚀 Latest Enhancements (v4.0)

### **🔥 Major Feature Additions**

#### **1. Enhanced Swagger Documentation for Flow Groups**

**Problem Solved:** Swagger examples for Flow Group POST and PUT endpoints included unnecessary fields that should be auto-populated or not editable.

**Changes Made:**
- **POST /api/flow-groups**: Request body example now only shows `flowGroupName` and `flows` (other fields like `id`, `createdAt`, `updatedAt`, `currentIteration`, `revolutions` are auto-populated)
- **PUT /api/flow-groups/{id}**: Request body example now only shows `flowGroupName` and `flows` (fields like `currentIteration`, `revolutions` are not editable, `id` is passed as path parameter)
- Added `@Schema` annotations with proper examples to `FlowGroupCreateDto` and `FlowGroupUpdateDto`

**Impact:** API documentation now accurately reflects the expected request payloads, improving developer experience and reducing confusion.

#### **2. New Flow Group Details Endpoint with Advanced Filtering**

**Problem Solved:** No way to retrieve aggregated flow group details with pagination, filters, and query parameters for rendering accordion tables.

**New Endpoint:**
- `GET /api/flow-groups/details` - Get flow group details with associated flows aggregated by flow group name
- **Supports:** Pagination, filtering by `flowGroupName` (partial match, case-insensitive), sorting parameters
- **Response Format:**
```json
{
  "flowGroups": {
    "Smoke": [
      {
        "flowId": 1,
        "squashTestCase": "Login Test Case",
        "squashTestCaseId": 12345,
        "createdAt": "2026-01-04T21:09:45.421804",
        "updatedAt": "2026-01-06T21:09:45.421804"
      }
    ],
    "Sanity": [...]
  }
}
```

**Impact:** Enables efficient rendering of accordion-style tables categorized by flow groups, with full pagination and filtering support.

#### **3. Enhanced Flow Execution Search with Flow Group Name Filter**

**Problem Solved:** Flow execution search lacked the ability to filter by flow group name.

**Changes Made:**
- Added `flowGroupName` parameter to `GET /api/flow-executions/search`
- Supports partial matching with case-insensitive LIKE query
- Updated repository query to join with `FlowGroup` table for name-based filtering

**Impact:** Comprehensive search capabilities across all flow execution metadata, enabling better analysis and monitoring of grouped executions.

### **🔧 Technical Implementation Details**

#### **Swagger Schema Updates:**
```java
@Schema(example = """
    {
      "flowGroupName": "string",
      "flows": [0]
    }
    """)
public class FlowGroupCreateDto {
    // Only flowGroupName and flows fields
}
```

#### **Enhanced Repository Query:**
```sql
SELECT fe FROM FlowExecution fe LEFT JOIN FlowGroup fg ON fe.flowGroupId = fg.id
WHERE (:flowGroupName IS NULL OR LOWER(fg.flowGroupName) LIKE LOWER(CONCAT('%', :flowGroupName, '%')))
```

#### **Flow Group Details Aggregation:**
- Modified `FlowGroupDetailsDto` to use `List<FlowSummaryDto>` instead of array
- Added filtering logic in service layer for `flowGroupName` parameter
- Maintained backward compatibility with existing endpoint behavior

### **🛡️ Backward Compatibility**

**✅ Zero Breaking Changes:**
- All existing endpoints work unchanged
- New parameters are optional
- Response formats maintained
- Existing swagger examples still functional

### **🎯 Usage Examples**

#### **Filtered Flow Group Details**
```bash
# Get all flow group details
GET /api/flow-groups/details

# Filter by flow group name
GET /api/flow-groups/details?flowGroupName=smoke

# With pagination and sorting
GET /api/flow-groups/details?page=0&size=10&sortBy=flowGroupName&sortDirection=ASC
```

#### **Advanced Flow Execution Search**
```bash
# Search by flow group name
GET /api/flow-executions/search?flowGroupName=E2E&page=0&size=20

# Combined filters
GET /api/flow-executions/search?flowGroupName=regression&fromDate=2024-01-01T00:00:00&toDate=2024-01-31T23:59:59
```

### **📊 Performance & Documentation Improvements**

**API Documentation:**
- Cleaner, more accurate swagger examples
- Reduced confusion about required vs auto-populated fields
- Better developer onboarding experience

**Query Performance:**
- Efficient LEFT JOIN for flow group name filtering
- Partial matching with database-level LIKE queries
- Maintained existing query performance for other filters

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

When contributing to the new v2.0 features:
- Ensure backward compatibility is maintained
- Add appropriate tests for new functionality
- Update API documentation for new endpoints
- Follow the established error handling patterns

1.  Fork the repository.
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

## 📄 License

This project is licensed under the MIT License. See the `LICENSE` file for details.

---

**Orkestra v4.0** - Orchestrating seamless E2E test automation workflows with enhanced efficiency, intelligent resource management, powerful multi-flow execution capabilities, FlowGroup orchestration with iteration tracking, and advanced filtering and search capabilities.
