prompt 1


# Java Backend Interview — Real Codebase Examples

**Codebase:** MBS/ATC Enterprise Billing System (MBS API, MBS GUI, ATC Backend/Frontend)

---

## SECTION A: SOLID Principles

---

### A1. SINGLE RESPONSIBILITY PRINCIPLE (SRP)

#### Good Example: `WorkflowTransitionService`
**File:** [atc/backend/src/main/java/com/lumen/atc/service/WorkflowTransitionService.java](atc/backend/src/main/java/com/lumen/atc/service/WorkflowTransitionService.java)

```java
@Service
public class WorkflowTransitionService {

    @Autowired
    private WorkflowTransitionRepository workflowTransitionRepository;

    public WorkflowTransition addWorkflowTransition(Long projectDataId, String fromStatus, String toStatus, String userId) {
        WorkflowTransition transition = new WorkflowTransition();
        transition.setProjectDataId(projectDataId);
        transition.setTransitionFrom(fromStatus);
        transition.setTransitionTo(toStatus);
        transition.setCreatedDttm(LocalDateTime.now());
        transition.setOriginatorId(userId);
        return workflowTransitionRepository.save(transition);
    }

    public List<WorkflowTransition> getWorkflowTransitionsByProjectDataId(Long projectDataId) {
        return workflowTransitionRepository.findByProjectDataIdOrderByWorkflowTransitionIdDesc(projectDataId);
    }
}
```

**Explanation:** This class has exactly one responsibility — recording and retrieving project status transitions. It has only one reason to change: if the workflow transition data model changes. It does not contain business logic about *when* to transition; other services call it when they need to record a transition.

**Interview Answer:** "WorkflowTransitionService follows SRP perfectly — it only manages the persistence of status transitions, while the decision of *when* to transition lives in the calling services like ProjectEditService or MBSPaymentHistoryScheduler."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

#### SRP Violation: `BNCController` (48 endpoints in one controller)
**File:** [mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BNCController.java](mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BNCController.java)

```java
@Controller
public class BNCController {
    @Autowired private ProductDetailService billCodeService;
    @Autowired private BCStateService billStateService;
    @Autowired private BillInvoiceService billInvoiceService;
    @Autowired private BCPdfBillService pdfBillService;
    @Autowired private MBSBillPullDetailService billPullService;
    @Autowired private AuditInfoService auditService;
    @Autowired private BillBalHistoryService billBalHistoryService;
    // ... 10+ more autowired dependencies

    @GetMapping("/bncHomePage") ...
    @GetMapping("/billCodeLookUP") ...
    @GetMapping("/showBill") ...
    @GetMapping("/pdFBillSummary") ...
    @GetMapping("/showAdjustment") ...
    @GetMapping("/admin") ...
    @GetMapping("/customerDetail") ...
    @PostMapping("/updateBillReRunIndrToY") ...
    // ... 48 total endpoints spanning billing, PDF, adjustments, admin, rerun, etc.
}
```

**Explanation:** This controller has 48 request mappings and 10+ autowired dependencies, covering billing pages, PDF generation, adjustment views, admin screens, bill rerun triggers, and report generation. It has many reasons to change — a textbook SRP violation. It should be decomposed into domain-specific controllers (BillingController, PdfBillController, AdminController, etc.).

**Interview Answer:** "BNCController violates SRP with 48 endpoints and 10+ dependencies spanning billing, PDF, admin, and rerun concerns — it should be split into focused controllers, each with a single domain responsibility."

**AUTHORSHIP SIGNAL:** `@author ab79206`

---

### A2. OPEN/CLOSED PRINCIPLE (OCP)

**File:** [atc/backend/src/main/java/com/lumen/atc/service/CPPMMasterDataService.java](atc/backend/src/main/java/com/lumen/atc/service/CPPMMasterDataService.java) and [CPPMMasterDataServiceImpl.java](atc/backend/src/main/java/com/lumen/atc/service/CPPMMasterDataServiceImpl.java)

```java
// Interface — stable contract
@Service
public interface CPPMMasterDataService {
    CPPMMasterDataDTO searchMasterData(String projectId, String wbsElement);
    List<String> getAllProjectIds();
    List<String> getWbsByProject(String projectId);
    void saveInBatches(List<CppmMasterData> records, int batchSize);
    int cleanupOldBatches(Long currentBatchTrxId);
    List<String> autocompleteProjectIds(String query, Integer limit);
    boolean validateProjectId(String projectId, String wbsElement);
}

// Implementation — can be swapped/extended
@Service
@RequiredArgsConstructor
public class CPPMMasterDataServiceImpl implements CPPMMasterDataService {
    @Autowired private CppmMasterDataRepository repository;
    @PersistenceContext private EntityManager entityManager;

    @Override
    @Cacheable(value = "projectIdSearch", key = "#query + '_' + #limit", unless = "#result.isEmpty()")
    public List<String> autocompleteProjectIds(String query, Integer limit) { ... }

    @Override
    @Cacheable(value = "projectIdValidation", key = "#projectId + '_' + #wbsElement")
    public boolean validateProjectId(String projectId, String wbsElement) { ... }
}
```

**Explanation:** The `CPPMMasterDataService` interface defines a stable contract. The implementation adds caching via `@Cacheable` annotations without modifying the interface. A new implementation (e.g., one that fetches from an external CPPM API instead of a local DB) could be swapped in without changing any client code. The system is open for extension (new implementations) but closed for modification (the interface contract is stable).

**Interview Answer:** "The CPPMMasterDataService interface lets us extend behavior — like adding caching in the Impl — without modifying callers. A completely different data source could be plugged in by providing a new implementation."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### A3. LISKOV SUBSTITUTION PRINCIPLE (LSP)

**File:** [mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchBillRerunServiceImpl.java](BILL_RERUN_SOURCE_EXTRACTION.md) extends [BatchBillRerunHelper.java](BILL_RERUN_SOURCE_EXTRACTION.md)

```java
// Helper (parent) — provides reusable delete/update methods
public class BatchBillRerunHelper {
    protected boolean deleteBillComputeDetails(Date billPullDate, String systemName) { ... }
    protected boolean updateBillControl(String customerId, Date billPullDate, String systemName) { ... }
    protected boolean isBillReRunRequired(String systemName, Date billPullDate) { ... }
    // 20+ helper methods
}

// Impl (child) — adds orchestration, delegates to parent methods
@Service
public class BatchBillRerunServiceImpl extends BatchBillRerunHelper implements BatchBillRerunService {

    @Override
    @Transactional
    public boolean billReRun(String systemName, Date billPullDate) {
        // Orchestrates the parent's helper methods in sequence
        if (!deleteBillComputeDetails(billPullDate, systemName)) overallSuccess = false;
        if (!deleteBillCompute(billPullDate, systemName)) overallSuccess = false;
        // ... more steps
        if (!updateBillControlUsingDateAndSystem(billPullDate, systemName)) overallSuccess = false;
        return overallSuccess;
    }

    @Override
    public boolean isBillReRunRequired(String systemName, Date billPullDate) {
        return super.isBillReRunRequired(systemName, billPullDate);
    }
}
```

**Explanation:** `BatchBillRerunServiceImpl` extends `BatchBillRerunHelper` and can substitute for it wherever the parent type is expected. The subclass doesn't weaken any preconditions or strengthen postconditions — it reuses the parent's helper methods faithfully and adds orchestration logic. The `isBillReRunRequired()` method directly delegates to `super`, showing transparent substitutability. This follows LSP.

**Interview Answer:** "BatchBillRerunServiceImpl extends BatchBillRerunHelper without violating any parent contracts — it reuses parent helper methods in its orchestration flow and even directly delegates isBillReRunRequired to super, demonstrating clean LSP compliance."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### A4. INTERFACE SEGREGATION PRINCIPLE (ISP)

#### Focused Interface: `BatchBillRerunService`
**File:** `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchBillRerunService.java`

```java
@Service
public interface BatchBillRerunService {
    BillRerunResult runBillReRunForSystem(String systemName);
    boolean runBillReRunForCustomer(String customerId, Date billPullDate, String systemName);
    boolean billReRun(String systemName, Date billPullDate);
    boolean billReRunForCustomer(String systemName, Date billPullDate, String customerId);
    boolean isBillReRunRequired(String systemName, Date billPullDate);
}
```

#### Another Focused Interface: `BatchFormattingInvoiceService`
**File:** [mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchFormattingInvoiceService.java](mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchFormattingInvoiceService.java)

```java
@Service
public interface BatchFormattingInvoiceService {
    InvoicePDFResponseDTO generateInvoicePdf(String systemName, String billDate, String customerId, boolean preview);
    void generateAndPersistInvoices(List<String> customerIds, String systemName, String billDate, boolean preview);
    void generatePdfForSystem(String systemName, boolean preview);
}
```

**Explanation:** The codebase follows ISP by splitting batch processing into focused interfaces — `BatchBillRerunService` (5 methods, all rerun-related), `BatchFormattingInvoiceService` (3 methods, all PDF formatting), `BatchBillingInvoiceService`, `BatchTaxingInvoiceService`, and `RatingService`. Each client (e.g., `BatchInvoiceController`) only depends on the interfaces it needs. No client is forced to depend on methods it doesn't use.

**Interview Answer:** "The batch services are segregated into small interfaces — rerun, formatting, billing, taxing, rating — each with 3-5 methods. The controller autowires only the interfaces it needs, following ISP."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### A5. DEPENDENCY INVERSION PRINCIPLE (DIP)

**File:** [atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java](atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java)

```java
@Service
public class ProjectEditService {

    @Autowired ProjectDataRepository projectDataRepository;       // Repository abstraction
    @Autowired CustomerRepository customerRepository;              // Repository abstraction
    @Autowired NotesRepository notesRepository;                    // Repository abstraction
    @Autowired WorkflowTransitionService workflowTransitionService;// Service abstraction
    @Autowired StateDetailService stateDetailService;              // Service abstraction
    @Autowired ProjectDataService projectDataService;              // Interface type
    @Autowired MBSServiceAdaptor mbsServiceAdaptor;               // External system adaptor
    @Autowired TransactionTemplate transactionTemplate;            // Spring abstraction
    @Autowired BillingRequestBuilderService billingRequestBuilderService; // Builder service
}
```

**Explanation:** `ProjectEditService` depends entirely on abstractions — Spring-managed interfaces and repositories injected via `@Autowired`. It never instantiates concrete classes directly. The `MBSServiceAdaptor` and `BillingRequestBuilderService` are injected rather than created internally, so the external system integration can be mocked in tests. The `TransactionTemplate` is also injected (not created), allowing Spring to manage its lifecycle.

**Interview Answer:** "ProjectEditService depends on 9 injected abstractions — repositories, services, adaptors, and TransactionTemplate. It never creates concrete dependencies, making it fully testable and decoupled from implementation details."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

## SECTION B: Design Patterns (Actually Used)

---

### B1. TEMPLATE METHOD

**File:** `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchBillRerunServiceImpl.java` extending `BatchBillRerunHelper.java`

```java
// Parent (BatchBillRerunHelper) defines the reusable steps:
protected boolean deleteBillComputeDetails(Date billPullDate, String systemName) { ... }
protected boolean deleteBillCompute(Date billPullDate, String systemName) { ... }
protected boolean deleteExpenseComputeDetails(Date billPullDate, String systemName) { ... }
protected boolean updatePaymentsAndAdjustments(Date billPullDate, String systemName) { ... }
protected boolean updateBillControlUsingDateAndSystem(Date billPullDate, String systemName) { ... }

// Child (BatchBillRerunServiceImpl) defines the skeleton algorithm:
@Transactional
public boolean billReRun(String systemName, Date billPullDate) {
    resetCounters();
    boolean overallSuccess = true;
    if (!deleteBillComputeDetails(billPullDate, systemName)) overallSuccess = false;
    if (!deleteBillCompute(billPullDate, systemName)) overallSuccess = false;
    if (!deleteExpenseComputeDetails(billPullDate, systemName)) overallSuccess = false;
    if (!deleteExpenseCompute(billPullDate, systemName)) overallSuccess = false;
    if (!deleteTaxComputeDetails(billPullDate, systemName)) overallSuccess = false;
    if (!deleteTaxCompute(billPullDate, systemName)) overallSuccess = false;
    if (!deleteBillInvoiceFiles(billPullDate, systemName)) overallSuccess = false;
    if (!deleteAssignDetails(billPullDate)) overallSuccess = false;
    if (!deleteExpenseVouchers(billPullDate)) overallSuccess = false;
    if (!updatePaymentsAndAdjustments(billPullDate, systemName)) overallSuccess = false;
    if (!deleteAffiliateAPAYs(billPullDate, systemName)) overallSuccess = false;
    // ... update usage, update bill control, update bill pull detail
    logBillRerunSummary(systemName, billPullDate, overallSuccess);
    return overallSuccess;
}
```

**Explanation:** This is a variant of Template Method where the parent class (`BatchBillRerunHelper`) provides the individual step implementations (delete records, update flags), and the child class (`BatchBillRerunServiceImpl`) defines the algorithm skeleton — the exact sequence of steps. The child also provides a `billReRunForCustomer()` variant that uses the same helper methods but with customer-level overloaded signatures.

**Interview Answer:** "The bill rerun uses a Template Method variant — the helper parent provides step implementations, and the child defines the algorithm skeleton in billReRun() and billReRunForCustomer(), reusing the same steps with different parameters."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### B2. BUILDER

**File:** [atc/backend/src/main/java/com/lumen/atc/service/BillingRequestBuilderService.java](atc/backend/src/main/java/com/lumen/atc/service/BillingRequestBuilderService.java)

```java
@Service
public class BillingRequestBuilderService {

    @Autowired AppPropertiesCacheService appPropertiesCacheService;
    @Autowired CustomerService customerService;
    @Autowired StateDetailService stateDetailService;
    @Autowired ProductDetailRepository productDetailRepository;
    @Autowired ProjectDataRepository projectDataRepository;

    public CustomerBillingRequest prepareCustomerDataForBillingRequest(Long projectDataId, CustomerOperation operation) {
        // Step-by-step construction of a complex billing request
        CustomerBillingRequest request = buildCustomerBillingRequest(projectData, customer, operation);
        // Internally calls:
        //   buildBillingAddress(customer)
        //   buildCustomerConfiguration(projectData)
        //   buildServiceInformation(projectData)
        //   buildBillingSegment(projectData)
        //   buildBillingProductSegment(projectData, products)
        return request;
    }
}
```

**Explanation:** `BillingRequestBuilderService` constructs a complex `CustomerBillingRequest` DTO step-by-step using multiple builder methods (`buildBillingAddress`, `buildCustomerConfiguration`, `buildServiceInformation`, `buildBillingSegment`, `buildBillingProductSegment`). Each method builds one piece of the request, and the top-level method orchestrates the construction. This is the Builder pattern applied as a service rather than the classic fluent-API style.

**Interview Answer:** "BillingRequestBuilderService implements the Builder pattern as a service — it constructs a complex CustomerBillingRequest through sequential build methods for address, configuration, service info, and billing segments, separating construction from representation."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### B3. STRATEGY

**No pure Strategy pattern found.** The codebase uses if-else/switch chains instead.

**Closest example:** [mbs-app-API/src/main/java/com/lumen/mbs/controller/PostPaymentController.java](mbs-app-API/src/main/java/com/lumen/mbs/controller/PostPaymentController.java)

```java
// Transaction type dispatched via if-else chain (NOT a Strategy pattern):
if(mPaymtTransType.equals("Refund")) {
    postPaymentResponse.getResponseInfo().getErrorInfo().setErrorCode("BARTMBSERR4");
    postPaymentResponse.getResponseInfo().getErrorInfo().setErrorMessage("REFUND ERROR NO CUSTOMER ID IN REQUEST");
} else if(mPaymtTransType.equals("Chargeback")) {
    postPaymentResponse.getResponseInfo().getErrorInfo().setErrorCode("BARTMBSERR5");
    postPaymentResponse.getResponseInfo().getErrorInfo().setErrorMessage("CHARGEBACK ERROR NO CUSTOMER ID IN REQUEST");
} else if(mPaymtTransType.equals("ChargebackReversal")) {
    postPaymentResponse.getResponseInfo().getErrorInfo().setErrorCode("BARTMBSERR6");
    postPaymentResponse.getResponseInfo().getErrorInfo().setErrorMessage("CHARGEBACK REVERSAL ERROR NO CUSTOMER ID IN REQUEST");
} else {
    postPaymentResponse = mbsPaymentService.paymentWithoutcustomerId(mInvoiceNumber, postPaymentRequest);
}
```

**Explanation:** Payment transaction types (Refund, Chargeback, ChargebackReversal, normal payment) are dispatched via if-else chains rather than the Strategy pattern. A Strategy refactoring would define a `PaymentStrategy` interface with implementations like `RefundStrategy`, `ChargebackStrategy`, etc., registered in a map by transaction type. The current approach is fragile — adding a new transaction type requires modifying the controller's if-else chain.

**Interview Answer:** "The codebase doesn't use the Strategy pattern — payment transaction types are dispatched via if-else chains in the controller. A Strategy refactoring with a Map<String, PaymentStrategy> would be cleaner and follow OCP."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### B4. FACTORY

**No Factory pattern found in the codebase.** Object creation is handled by Spring's DI container (`@Autowired`, `@Service`, `@Component`) rather than explicit Factory classes.

---

### B5. DTO

**File:** [atc/backend/src/main/java/com/lumen/atc/dto/CompositeProjectEditResponse.java](atc/backend/src/main/java/com/lumen/atc/dto/CompositeProjectEditResponse.java)

```java
public class CompositeProjectEditResponse {

    private Long projectDataId;
    private String status;           // "SUCCESS" / "ERROR"
    private String message;          // Human-readable result description
    private String projectStatus;    // Current project status after edit
    private List<String> appliedOperations;  // ["UPDATE_BILLING_INFO", "UPDATE_NEGOTIATED_AMOUNT"]
    private List<String> modifiedFields;     // ["billAddress", "billCity", "billNegotiatedAmount"]
    private LocalDateTime updatedAt;

    public CompositeProjectEditResponse(Long projectDataId, String status, String message,
            String projectStatus, List<String> appliedOperations, List<String> modifiedFields,
            LocalDateTime updatedAt) { ... }

    // getters and setters
}
```

**Explanation:** `CompositeProjectEditResponse` is a DTO that carries results from the service layer to the API client. It contains only the fields the client needs (status, message, applied operations, modified fields) without exposing internal entities like `ProjectData` or `Customer`. This prevents JPA entities from leaking into the API contract, avoids lazy-loading issues, and decouples the API schema from the database schema. The codebase has 82+ DTOs across all modules.

**Interview Answer:** "DTOs like CompositeProjectEditResponse decouple the API contract from JPA entities — they carry only what the client needs, prevent lazy-loading issues, and let the database schema evolve independently of the API."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### B6. ADAPTER

**File:** [atc/backend/src/main/java/com/lumen/atc/service/adaptors/MBSServiceAdaptor.java](atc/backend/src/main/java/com/lumen/atc/service/adaptors/MBSServiceAdaptor.java)

```java
@Component
@RequiredArgsConstructor
public class MBSServiceAdaptor {

    @Autowired private OAuthTokenManager oauthTokenManager;
    @Autowired private AppPropertiesCacheService appPropertiesCacheService;
    @Autowired private RestTemplate secureRestTemplate;
    @Autowired private CgsAdapter cgsAdapter;

    // Adapts MBS API: OAuth token → HTTP call → domain DTO
    public String fetchCustomerId() throws Exception {
        String token = oauthTokenManager.getValidToken();
        String url = appPropertiesCacheService.getPropertyValue("MBS_CUSTOMER_ID_URL");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = secureRestTemplate.exchange(url + "?systemName={systemName}",
                HttpMethod.GET, new HttpEntity<>(headers), String.class, systemName);
        return response.getBody().trim();
    }

    // Adapts CGS Tax API: builds XML request → calls CGS → returns BigDecimal
    public BigDecimal fetchTotalTax(ProjectData projectData, BigDecimal amountToBill) throws Exception {
        String requestXml = cgsAdapter.createCgsTaxRequestXml(...);
        RetrieveTaxDetailsByAddressResponse responseObj = cgsAdapter.makeCgsTaxApiCall(requestXml, taxDescPfx);
        // ... iterate tax rates, sum amounts
        return totalTaxAmt.setScale(2, RoundingMode.HALF_UP);
    }

    // Adapts MBS Payment History API: HTTP call → List<PaymentDTO>
    public List<PaymentDTO> getPaymentsInfoFromMBS(String custId) throws Exception { ... }
}
```

**Explanation:** The codebase has 5 Adapter classes that wrap external system APIs behind clean internal interfaces. `MBSServiceAdaptor` adapts the MBS billing system, `BRIMServiceAdaptor` adapts the BRIM payment system, `CgsAdapter` adapts the CGS tax calculation API, `AgiloftAdaptor` adapts the Agiloft document management system, and `BSPURLServiceAdaptor` adapts the secure payment portal. Each adapter handles OAuth tokens, HTTP calls, request/response transformation, and error translation — isolating the core business logic from external system details.

**Interview Answer:** "We use 5 Adapter classes (MBS, BRIM, CGS, Agiloft, BSPURL) that wrap external APIs behind clean interfaces — handling OAuth, HTTP calls, and response transformation so the core services never deal with external API details."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

## SECTION C: Transaction Management

---

### C1. @TRANSACTIONAL USAGE

The codebase has **160+ @Transactional annotations** across 55+ files. Key patterns:

#### Pattern 1: Default Propagation (REQUIRED) — Most Common
**File:** [atc/backend/src/main/java/com/lumen/atc/service/BatchTransactionService.java](atc/backend/src/main/java/com/lumen/atc/service/BatchTransactionService.java)

```java
@Service
public class BatchTransactionService {

    private final BatchTransactionRepository repository;

    public BatchTransactionService(BatchTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BatchTransaction createBatch(String batchType, String fileName) {
        BatchTransaction batch = new BatchTransaction();
        batch.setBatchStatus(BatchStatus.CREATED);
        batch.setCreateDttm(LocalDateTime.now());
        return repository.save(batch);
    }

    @Transactional
    public void markInProgress(Long batchTrxId) {
        updateStatus(batchTrxId, BatchStatus.IN_PROGRESS);
    }

    @Transactional
    public void markProcessed(Long batchTrxId, long recordCount) {
        BatchTransaction batch = repository.findById(batchTrxId).orElseThrow();
        batch.setBatchStatus(BatchStatus.PROCESSED);
        batch.setBatchRecordCount(recordCount);
    }

    @Transactional
    public void markFailed(Long batchTrxId) {
        updateStatus(batchTrxId, BatchStatus.FAILED);
    }
}
```

**Self-invocation risk:** None here — each method is called from external callers, so the proxy intercepts correctly.

#### Pattern 2: `Propagation.REQUIRES_NEW` — Transaction Isolation
**File:** [atc/backend/src/main/java/com/lumen/atc/service/CPPMMasterDataServiceImpl.java](atc/backend/src/main/java/com/lumen/atc/service/CPPMMasterDataServiceImpl.java#L183)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveInBatches(List<CppmMasterData> records, int batchSize) {
    for (int i = 0; i < total; i++) {
        entityManager.persist(records.get(i));
        if (i > 0 && i % batchSize == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }
    entityManager.flush();
    entityManager.clear();
}
```

**Why REQUIRES_NEW:** Creates an independent transaction so a failure in this batch insert doesn't roll back the calling transaction (which manages the overall batch processing lifecycle). Also used in `BatchFormattingInvoiceServiceImpl.generateInvoicePdf()` and `MbsTaxComputeDetailCalc` for the same isolation reason.

#### Pattern 3: `readOnly = true` — Query Optimization
**File:** [atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java](atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java#L94)

```java
@Transactional(readOnly = true)
public ProjectEditDetailsResponse getProjectEditDetails(Long projectDataId) {
    ProjectData projectData = getProject(projectDataId);
    return new ProjectEditDetailsResponse(
            projectData.getProjectDataId(), ... ,
            mapBillingInfo(projectData, resolveExistingCustomer(projectData))
    );
}
```

**Why readOnly:** Tells the JPA provider this transaction won't modify data, allowing optimizations (skip dirty checking, use read replicas). Only one method in the entire codebase uses this — it's an underutilized optimization.

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### C2. PROGRAMMATIC TRANSACTIONS — TransactionTemplate

**File:** [atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java](atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java#L115)

```java
@Autowired
TransactionTemplate transactionTemplate;

public CompositeProjectEditResponse editProject(Long projectDataId, CompositeProjectEditRequest request) {
    validateCompositeRequest(request);

    // DB changes happen inside this transaction
    CompositeEditResult result = transactionTemplate.execute(status -> applyCompositeEdit(projectDataId, request));

    if (result == null) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save project edits.");
    }

    // MBS sync happens OUTSIDE the transaction — if it fails, DB changes are already committed
    syncMbsIfRequired(projectDataId, result);

    return buildCompositeResponse(projectDataId, result);
}
```

**Explanation:** `TransactionTemplate` is used instead of `@Transactional` because the method needs to separate the DB transaction from the external MBS API call. The `applyCompositeEdit()` method (which saves to PROJECT_DATA, CUSTOMER_ATC, NOTES, WORKFLOW_TRANSITION) runs inside the transaction. The `syncMbsIfRequired()` method (which calls the MBS external API) runs *after* the transaction commits. If `@Transactional` were used on the whole method, an MBS API timeout would roll back all the already-successful DB changes.

**Interview Answer:** "TransactionTemplate is used in ProjectEditService because we need DB changes committed before calling the MBS external API — if we used @Transactional on the whole method, an API timeout would roll back local DB changes."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### C3. TRANSACTION BOUNDARIES — Multiple DB Ops in One Transaction

**File:** `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/BatchBillRerunServiceImpl.java`

```java
@Override
@Transactional
public boolean billReRun(String systemName, Date billPullDate) {
    resetCounters();
    boolean overallSuccess = true;

    // 12 distinct database operations in ONE transaction:
    if (!deleteBillComputeDetails(billPullDate, systemName)) overallSuccess = false;
    if (!deleteBillCompute(billPullDate, systemName)) overallSuccess = false;
    if (!deleteExpenseComputeDetails(billPullDate, systemName)) overallSuccess = false;
    if (!deleteExpenseCompute(billPullDate, systemName)) overallSuccess = false;
    if (!deleteTaxComputeDetails(billPullDate, systemName)) overallSuccess = false;
    if (!deleteTaxCompute(billPullDate, systemName)) overallSuccess = false;
    if (!deleteBillInvoiceFiles(billPullDate, systemName)) overallSuccess = false;
    if (!deleteAssignDetails(billPullDate)) overallSuccess = false;
    if (!deleteExpenseVouchers(billPullDate)) overallSuccess = false;
    if (!updatePaymentsAndAdjustments(billPullDate, systemName)) overallSuccess = false;
    if (!deleteAffiliateAPAYs(billPullDate, systemName)) overallSuccess = false;
    if (!updateBillControlUsingDateAndSystem(billPullDate, systemName)) overallSuccess = false;

    if (overallSuccess) { updateBillPullDetail(billPullDate, systemName); }
    return overallSuccess;
}
```

**Rollback behavior:** Because the entire method is `@Transactional`, if any unchecked exception is thrown, ALL 12 operations roll back atomically. However, the helper methods catch exceptions and return `false` instead of throwing, so individual step failures set `overallSuccess = false` but do NOT trigger rollback. This means **partial state is possible** — some deletes succeed while others fail, and everything still commits. This is an intentional design choice (continue-on-error) but could leave the billing data in an inconsistent state.

**Interview Answer:** "The billReRun method wraps 12 DB operations in one @Transactional, but helper methods catch exceptions and return false instead of re-throwing, so partial failures commit rather than rolling back — an intentional continue-on-error design that trades consistency for resilience."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

## SECTION D: JPA / Hibernate / Query Patterns

---

### D1. N+1 QUERY PROBLEM

**Risk site (unfixed):** [atc/backend/src/main/java/com/lumen/atc/service/MBSPaymentHistoryScheduler.java](atc/backend/src/main/java/com/lumen/atc/service/MBSPaymentHistoryScheduler.java#L55)

```java
@Scheduled(cron = "0 30 2 * * ?")
public void fetchPaymentInfo() {
    List<ProjectData> pmtAwaitingProjects = projectDataService.getProjectsByStatuses(projectStatusesList);
    // N+1: For EACH project, makes an HTTP call + multiple DB saves
    for (ProjectData project : pmtAwaitingProjects) {
        String customerId = project.getCustomerId();
        String message = fetchPaymentInfoFromMBS(customerId, project.getProjectDataId(), project.getProjectStatus());
        // Inside fetchPaymentInfoFromMBS: 
        //   - calls proposalService.getProposalsByCustomerId(customerId)  ← DB query per iteration
        //   - calls paymentRepository.save(payment)                       ← DB write per iteration
        //   - calls projectDataService.updateProjectStatus(...)           ← DB write per iteration
        //   - calls workflowTransitionService.addWorkflowTransition(...)  ← DB write per iteration
    }
}
```

**Explanation:** The scheduler fetches all eligible projects in one query, then for each project, makes 4+ additional DB queries/writes inside the loop. This is the N+1 pattern — 1 query to fetch N projects, then N×4 queries inside the loop. The codebase doesn't use `JOIN FETCH` anywhere (no `JOIN FETCH` found in any repository). Because the entities use `@IdClass` composite keys with no `@OneToMany`/`@ManyToOne` relationships, the N+1 problem manifests through explicit per-item queries rather than lazy-loaded relationships.

**Interview Answer:** "The MBSPaymentHistoryScheduler has an N+1 pattern — it fetches N projects, then makes 4+ DB calls per project in a loop. Because the codebase uses flat entities without JPA relationships, the N+1 comes from explicit queries rather than lazy loading. No JOIN FETCH is used anywhere."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### D2. JPQL vs NATIVE QUERIES

#### JPQL Example
**File:** [mbs-app-API/src/main/java/com/lumen/mbs/repository/BillControlRespository.java](mbs-app-API/src/main/java/com/lumen/mbs/repository/BillControlRespository.java)

```java
@Query("SELECT bc FROM BillControl bc WHERE TRIM(bc.custId) = TRIM(:custId) 
        AND TRUNC(bc.billPullDate) = :billPullDate 
        AND TRIM(bc.systemName) = TRIM(:systemName)")
List<BillControl> findByCustIdAndBillPullDateAndSystemName(String custId, Date billPullDate, String systemName);
```

#### Native Query Example
**File:** [mbs-app-API/src/main/java/com/lumen/mbs/repository/BillControlRespository.java](mbs-app-API/src/main/java/com/lumen/mbs/repository/BillControlRespository.java)

```java
@Query(value = "SELECT DISTINCT bc.CUSTOMER_ID AS customerId, bp.bill_pull_date AS billPullDate "
        + "FROM bill_control bc "
        + "JOIN billpull_detail bp ON TO_CHAR(bc.bill_pull_date, 'YYYY-MM-DD') = TO_CHAR(bp.bill_pull_date, 'YYYY-MM-DD') "
        + "WHERE TO_CHAR(bp.bill_pull_date, 'YYYY-MM-DD') = TO_CHAR(:billPullDate, 'YYYY-MM-DD') "
        + "AND (bc.BILL_TYPE IN ('A' , 'N')) "
        + "AND TRIM(bc.system_name) = :systemName AND TRIM(bc.bill_complete) = :processValBefore "
        + "AND TRIM(bp.bill_run_indr) = :processValBefore AND TRIM(bp.bill_media_indr) = :processValBefore "
        + "AND TRIM(bp.bill_rel_indr) = :processValAfter", nativeQuery = true)
List<Object[]> GetMbsCustomersFromBCBP(...);
```

**Explanation:** Native SQL was chosen here because the query uses Oracle-specific functions (`TO_CHAR`, `TRUNC`), joins across tables not modeled as JPA relationships, and returns `Object[]` projections rather than full entities. JPQL can't express `TO_CHAR(date, 'YYYY-MM-DD')` formatting natively. The codebase has 28+ native query methods across 15 repository files, all using Oracle-specific SQL.

**Interview Answer:** "Native SQL is used when queries need Oracle-specific functions like TO_CHAR and TRUNC, cross-table joins not modeled in JPA, or Object[] projections — 28+ such queries exist in the codebase."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### D3. COMPOSITE KEYS — @IdClass

**File:** `mbs-app-API/src/main/java/com/lumen/mbs/model/BillControl.java` and `BillControlPK.java`

```java
// Entity with composite key
@Entity
@IdClass(BillControlPK.class)
@Table(name = "BILL_CONTROL")
public class BillControl {
    @Id @Column(name = "CUSTOMER_ID") private String custId;
    @Id @Column(name = "BILL_PULL_DATE") private Date billPullDate;
    @Id @Column(name = "SYSTEM_NAME") private String systemName;
    // Non-key fields...
}

// PK class — must implement Serializable, override equals/hashCode
public class BillControlPK implements Serializable {
    private String custId;
    private Date billPullDate;
    private String systemName;

    @Override
    public boolean equals(Object obj) { ... }
    @Override
    public int hashCode() { ... }
}
```

**Explanation:** The codebase uses `@IdClass` exclusively (38+ entities) — never `@EmbeddedId`. The gotchas: (1) The PK class must implement `Serializable`, (2) it must have a no-arg constructor, (3) `equals()` and `hashCode()` must be consistent, (4) the PK field names must exactly match the entity's `@Id` field names, (5) `Date` in composite keys can cause issues with time components — note the use of `TRUNC()` in queries to strip time.

**Interview Answer:** "The codebase uses @IdClass for all 38+ composite keys. Key gotchas: PK class must be Serializable with matching field names, and Date fields in keys require TRUNC() in queries to avoid time-component mismatches."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### D4. QUERY PERFORMANCE RISKS

#### Risk 1: TRIM() Preventing Index Usage (101 occurrences)
**File:** Multiple repository files across MBS API and GUI

```java
// From BillControlRespository.java — TRIM() on BOTH sides prevents index usage
@Query("SELECT bc FROM BillControl bc WHERE TRIM(bc.custId) = TRIM(:custId) 
        AND TRIM(bc.systemName) = TRIM(:systemName)")

// From BCAdjustmentRepository.java — Multiple TRIM() calls
@Query("SELECT a FROM BCAdjustment a WHERE TRIM(a.carrierId) = TRIM(:custId) 
        AND TRIM(a.systemName) = TRIM(:systemName)")
```

**Concern:** There are **101 TRIM() occurrences** across repository queries. Wrapping a column in `TRIM()` prevents the Oracle optimizer from using a B-tree index on that column, forcing a full table scan. The fix: clean data at insert time and use direct equality comparisons, or create function-based indexes on `TRIM(column)`.

#### Risk 2: DISTINCT Masking Join Problems
```java
// From BillControlRespository.java
@Query(value = "SELECT DISTINCT(bc.billPullDate) FROM BillControl bc WHERE ...")
@Query(value = "SELECT DISTINCT(bc.custId) FROM BillControl bc WHERE ...")
```

**Concern:** `DISTINCT` may be masking a join duplication problem. If the underlying data model is clean, `DISTINCT` shouldn't be needed — its presence suggests either the query joins are producing duplicates or the data has integrity issues.

**Interview Answer:** "The codebase has 101 TRIM() calls in WHERE clauses that prevent B-tree index usage, forcing full table scans. The fix is cleaning data at insert time or adding function-based indexes."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

## SECTION E: Error Handling & Resilience

---

### E1. EXCEPTION HANDLING PATTERNS — Boolean vs Throw

#### Boolean-on-failure pattern:
**File:** `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/helper/BatchBillRerunHelper.java`

```java
protected boolean deleteBillComputeDetails(Date billPullDate, String systemName) {
    try {
        int deleted = billComputeDetailRepository.deleteBillComputeDetailByBillDateAndSystemName(billPullDate, systemName);
        cntBillComputeDetailDeleted += deleted;
        return true;
    } catch (Exception e) {
        logger.logError(log, "Error deleting BillComputeDetail: " + e.getMessage(), e);
        return false;  // Swallows exception, returns boolean
    }
}
```

#### Throw/propagate pattern:
**File:** [atc/backend/src/main/java/com/lumen/atc/service/adaptors/MBSServiceAdaptor.java](atc/backend/src/main/java/com/lumen/atc/service/adaptors/MBSServiceAdaptor.java)

```java
public String fetchCustomerId() throws Exception {
    try {
        ResponseEntity<String> response = secureRestTemplate.exchange(...);
        if (response.getStatusCode() == HttpStatus.OK && StringUtils.hasText(response.getBody())) {
            return response.getBody().trim();
        }
        throw new ExternalServiceException("Customer ID not returned from MBS API");
    } catch (HttpStatusCodeException ex) {
        throw new ExternalServiceException("GetCustomerId API failed", ex);
    } catch (Exception ex) {
        throw new ExternalServiceException("Unexpected error in GetCustomerId API", ex);
    }
}
```

**Explanation:** The codebase uses both patterns for different reasons. The boolean pattern in `BatchBillRerunHelper` supports continue-on-error batch processing — if one delete fails, the rerun continues with the remaining steps. The throw pattern in `MBSServiceAdaptor` is used for external API calls where failure should propagate to the caller. The trade-off: boolean return hides the exception details and makes it harder to diagnose failures; throw propagation gives the caller full context to decide how to handle the error.

**Interview Answer:** "Boolean-return error handling is used in batch processing for continue-on-error semantics, while exception propagation is used for external API calls where callers need full failure context. The boolean approach trades diagnostic detail for resilience."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### E2. GRACEFUL DEGRADATION

**File:** [atc/backend/src/main/java/com/lumen/atc/service/adaptors/MBSServiceAdaptor.java](atc/backend/src/main/java/com/lumen/atc/service/adaptors/MBSServiceAdaptor.java#L296)

```java
public BigDecimal fetchTotalTax(ProjectData projectData, BigDecimal amountToBill) throws Exception {
    try {
        String requestXml = cgsAdapter.createCgsTaxRequestXml(...);
        RetrieveTaxDetailsByAddressResponse responseObj = cgsAdapter.makeCgsTaxApiCall(requestXml, taxDescPfx);
        // ... calculate totalTaxAmt from response
        return totalTaxAmt;

    } catch (HttpStatusCodeException ex) {
        // CGS API DOWN → MUST STOP — billing cannot proceed without tax
        throw new ExternalServiceException("CGS API HTTP failure", ex);

    } catch (ResponseDataException ex) {
        // CGS returned HTTP 200 but data issue → continue with zero tax
        log.warn("CGS response data issue, continuing with zero tax", ex);
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    } catch (CGSException ex) {
        // CGS logical/XML errors → DO NOT FAIL BUSINESS FLOW
        log.warn("CGS logical/XML error, continuing with zero tax. {}", ex.getTag(), ex);
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    } catch (Exception ex) {
        // Anything unexpected → still swallow per requirement
        log.warn("Unexpected CGS error, continuing with zero tax", ex);
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
```

**Explanation:** This method demonstrates tiered graceful degradation. HTTP-level failures (network down, auth error) propagate because billing can't proceed without the tax API. But business-level issues (empty tax data, XML parsing problems) are handled gracefully by returning zero tax — the billing process continues rather than failing entirely. This is a sound design choice: a network outage is a hard blocker, but a tax data issue shouldn't prevent invoice generation.

**Interview Answer:** "The tax calculation uses tiered degradation — HTTP failures propagate as hard errors, while business-level CGS issues return zero tax so billing continues. This differentiates infrastructure failures from data-quality issues."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### E3. INPUT VALIDATION

#### Controller-level validation (bad example):
**File:** [mbs-app-API/src/main/java/com/lumen/mbs/controller/PostPaymentController.java](mbs-app-API/src/main/java/com/lumen/mbs/controller/PostPaymentController.java#L130)

```java
// String comparison using == instead of .equals() — BUG
if ((mPaymentMethod == null) || (mPaymentMethod == "") || (mPaymentMethod == "string")) {
    errbuff.append("paymentMethod ");
    errRes = true;
}
if ((mTransType == null) || (mTransType == "") || (mTransType == "string")) {
    errbuff.append("paymentTransactionType ");
    errRes = true;
}
```

**Concern:** Uses `==` for String comparison instead of `.equals()`. The `mPaymentMethod == ""` check compares references, not values, so it will rarely match. This is a classic Java bug.

#### Date parsing validation (good example):
**File:** [mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java](mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java#L367)

```java
Date parsedDate;
try {
    parsedDate = MiscUtils.convertStringToTruncatedDate(billDate);
} catch (DateTimeParseException | IllegalArgumentException e) {
    return ResponseEntity.badRequest()
            .body("Invalid date format for billPullDate. Expected dd-MMM-yy (e.g., 22-MAY-25 / 22-MAY-2025).");
}
```

#### Composite request validation (good example):
**File:** [atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java](atc/backend/src/main/java/com/lumen/atc/service/ProjectEditService.java#L249)

```java
private void validateCompositeRequest(CompositeProjectEditRequest request) {
    if (request == null || !request.hasAnyOperation()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one edit operation is required.");
    }
    if (request.hasCancelOperation() && request.hasNonCancelOperation()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
            "Cancellation must be submitted without any other edits.");
    }
}
```

**Interview Answer:** "Validation patterns range from flawed (PostPaymentController uses == for String comparison) to well-structured (ProjectEditService validates business rules like mutual exclusivity of cancel vs. other edits). BatchInvoiceController properly validates date formats at the boundary."

**AUTHORSHIP SIGNAL:** PostPaymentController — no authorship signal. ProjectEditService — no authorship signal.

---

### E4. LOGGING — Most Detailed Example

**File:** `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/helper/BatchBillRerunHelper.java`

```java
protected void logBillRerunSummary(String systemName, Date billPullDate, boolean overallSuccess) {
    logger.logInfo(log, "Bill rerun summary for system=" + systemName + ", billPullDate=" + billPullDate + 
            ": Deleted: BillComputeDetail=" + cntBillComputeDetailDeleted + 
            ", BillCompute=" + cntBillComputeDeleted + 
            ", ExpenseComputeDetail=" + cntExpenseComputeDetailDeleted + 
            ", ExpenseCompute=" + cntExpenseComputeDeleted +
            ", TaxComputeDetail=" + cntTaxComputeDetailDeleted + 
            ", TaxCompute=" + cntTaxComputeDeleted + 
            ", BillInvoiceFiles=" + cntBillInvoiceFilesDeleted +
            ", AssignDetails=" + cntAssignDetailsDeleted + 
            ", ExpenseVouchers=" + cntExpenseVouchersDeleted +
            ", AffiliateAPAYs=" + cntAffiliateAPAYsDeleted +
            "; Updated: Payments=" + cntPaymentsUpdated + 
            ", Adjustments=" + cntAdjustmentsUpdated +
            ", UsageProcess=" + cntUsageProcessUpdated + 
            ", BillControl=" + cntBillControlUpdated + 
            ", BillPullDetail=" + cntBillPullDetailUpdated + 
            ", CurrUsage=" + totNumOfRowsUpdCurrUsg);
}
```

**Explanation:** This summary log captures 17 distinct counters after a bill rerun, providing complete audit trail for production debugging. It logs every delete count and update count across all affected tables. The MBS API uses a `DynamicLogger` component that checks a database-driven log level configuration hourly, allowing runtime log level changes without restart. However, the logging uses **string concatenation** (not parameterized logging), which means the string is built even when the log level is disabled — a minor performance concern.

**Contrast with ATC logging:** The ATC module uses **parameterized logging**: `logger.info("MBS sync completed successfully for projectDataId={} operation={}", projectDataId, operation)` — avoiding unnecessary string construction.

**Interview Answer:** "The bill rerun summary logs 17 counters across all affected tables for production audit. MBS uses string concatenation (performance cost), while ATC uses parameterized logging with {} placeholders — the latter is preferred because it avoids string construction when the level is disabled."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

## SECTION F: API Design

---

### F1. HTTP STATUS CODE USAGE

**File:** [mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java](mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java#L400)

```java
@PostMapping("/rerun-billing/system")
public ResponseEntity<String> rerunBillingForSystem(@RequestParam String systemName) {
    BillRerunResult result = batchBillRerunService.runBillReRunForSystem(systemName);

    if (allSuccessful) {
        status = HttpStatus.OK;                    // 200 — all cycles succeeded
        message = "Bill rerun for all cycles completed successfully...";
    } else if (allFailed) {
        status = HttpStatus.INTERNAL_SERVER_ERROR; // 500 — all cycles failed
        message = "Bill rerun for all cycles failed...";
    } else {
        status = HttpStatus.PARTIAL_CONTENT;       // 206 — some succeeded, some failed
        message = "Bill rerun partially completed...";
    }

    return ResponseEntity.status(status).body(message);
}
```

**Explanation:** This endpoint uses three distinct HTTP status codes: **200 OK** for complete success, **206 Partial Content** for partial success (some bill cycles processed, others failed), and **500 Internal Server Error** for complete failure. The 206 usage is creative but non-standard — RFC 7233 defines 206 for range requests (byte ranges in file downloads), not partial business results. A more RESTful approach would use 200 with a response body indicating partial success, or 207 Multi-Status (WebDAV). That said, this design gives API consumers an immediate signal about result completeness from the status code alone.

**Interview Answer:** "The rerun endpoint uses 200/206/500 to signal complete success, partial success, and complete failure. While 206 Partial Content is technically for byte-range requests per RFC 7233, it's a pragmatic choice that gives consumers an immediate signal without parsing the response body."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### F2. REST CONVENTIONS — Good vs Bad

#### Good REST (ATC module):
**File:** [atc/backend/src/main/java/com/lumen/atc/controller/ProjectDataController.java](atc/backend/src/main/java/com/lumen/atc/controller/ProjectDataController.java)

```java
@RestController
@RequestMapping("/projects")
@Tag(name = "Project Data", description = "Create Project APIs")
public class ProjectDataController {

    @PostMapping                                          // POST /projects
    public ResponseEntity<ProjectData> createProject(...) { ... }

    @GetMapping("/generateCustomerId")                    // GET /projects/generateCustomerId
    public ResponseEntity<String> generateCustomerIdFromMBS() { ... }

    @GetMapping("/{projectDataId}/edit")                  // GET /projects/{id}/edit
    public ResponseEntity<ProjectEditDetailsResponse> getProjectEditDetails(...) { ... }

    @PostMapping("/{projectDataId}/edit")                 // POST /projects/{id}/edit
    public ResponseEntity<CompositeProjectEditResponse> editProject(...) { ... }
}
```

**Analysis:** Resource-based URL (`/projects`), proper HTTP verbs (POST for create, GET for read), path params for resource identity (`/{projectDataId}`). However, `generateCustomerId` uses a verb-based name — `/projects/customer-ids` with POST would be more RESTful.

#### Bad REST (MBS GUI module):
**File:** [mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BNCController.java](mbs-app-GUI/mbsgui-master/src/main/java/com/ctl/mbs/controller/BNCController.java)

```java
@GetMapping("/bncHomePage")           // Verb-based, returns a view
@GetMapping("/billCodeLookUP")        // Verb-based, camelCase inconsistency
@GetMapping("/showBill")              // Verb-based
@GetMapping("/pdFBillSummary")        // Inconsistent casing
@GetMapping("/showAdjustment")        // Verb-based
@GetMapping("/showPmtSummary")        // Verb-based
@PostMapping("/showPdf")              // POST for what should be GET
@GetMapping("/getStateDetails")       // Verb-based with "get" prefix
@GetMapping("/getAllCountry")         // Verb-based, singular instead of plural
```

**Interview Answer:** "The ATC module follows REST conventions with resource-based URLs like /projects and proper HTTP verbs, while the MBS GUI uses verb-based URLs like /showBill and /getStateDetails — a legacy MVC pattern that predates REST conventions."

**AUTHORSHIP SIGNAL:** BNCController: `@author ab79206`. ProjectDataController: No authorship signal.

---

### F3. REQUEST/RESPONSE CONTRACTS

**Endpoint:** `POST /projects/{projectDataId}/edit`

**Request:**
```java
// CompositeProjectEditRequest — supports multiple simultaneous operations
{
    "cancel": {                              // Optional — cancel the project
        "userId": "ab12345",
        "cancellationReason": "Client withdrew",
        "notes": "Per email from client"
    },
    "statementOfWork": {                     // Optional — update SOW
        "userId": "ab12345",
        "statementOfWork": "Updated scope"
    },
    "billingInfo": {                         // Optional — update billing fields
        "userId": "ab12345",
        "customerId": "CUST001",
        "modifiedFields": ["billAddress", "billCity"],
        "billAddress": "123 Main St",
        "billCity": "Denver"
    },
    "negotiatedAmount": {                    // Optional — update amount
        "userId": "ab12345",
        "billNegotiatedAmount": 15000.00
    }
}
```

**Response:**
```java
// CompositeProjectEditResponse
{
    "projectDataId": 42,
    "status": "SUCCESS",
    "message": "Project details updated successfully.",
    "projectStatus": "ACTIVE",
    "appliedOperations": ["UPDATE_BILLING_INFO", "UPDATE_NEGOTIATED_AMOUNT"],
    "modifiedFields": ["billAddress", "billCity", "billNegotiatedAmount"],
    "updatedAt": "2026-07-01T14:30:00"
}
```

**Explanation:** This is a well-designed composite contract. The request supports multiple independent operations in a single call (reducing round-trips), with business validation that cancel is mutually exclusive with other operations. The response tells the client exactly which operations were applied and which fields changed, making it easy to update the UI accordingly.

**Interview Answer:** "The composite edit endpoint accepts multiple operations in one request, validates mutual exclusivity, and returns exactly which operations and fields were applied — a clean contract that reduces round-trips and gives the client full visibility."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### F4. API DOCUMENTATION — Swagger/OpenAPI

**File:** [mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java](mbs-app-API/src/main/java/com/lumen/mbs/controller/BatchInvoiceController.java)

```java
@RestController
@RequestMapping("/batch-invoice")
@Tag(name = "Batch Invoice Controller", description = "APIs for processing batch invoice operations")
public class BatchInvoiceController {

    @Operation(
            summary = "Triggers the complete invoice generation workflow",
            description = "Processes rating, billing, taxing, and formatting for all customers..."
    )
    @PostMapping("/process-complete-invoice")
    public ResponseEntity<String> processCompleteInvoice(
            @Parameter(description = "Bill date in dd-MMM-yyyy format", required = true) @RequestParam String billPullDate,
            @Parameter(description = "System name", required = true) @RequestParam String systemName) { ... }
}
```

**ATC uses @Hidden for internal endpoints:**
```java
@Hidden
@GetMapping("/address-templates")
public ResponseEntity<?> getAddressTemplates() { ... }

@Hidden
@GetMapping("/address-templates/{templateName}")
public ResponseEntity<?> getAddressTemplate(@PathVariable String templateName) { ... }
```

**Explanation:** The codebase uses OpenAPI 3 annotations (`@Tag`, `@Operation`, `@Parameter`, `@Hidden`) to build documentation directly into the code. `@Tag` groups endpoints by controller, `@Operation` provides summary/description for each endpoint, `@Parameter` documents request parameters with format hints, and `@Hidden` excludes internal/admin endpoints from the public API docs. The ATC module has 32 `@Operation` annotations and 19 `@Hidden` annotations, showing a deliberate separation between public and internal APIs.

**Interview Answer:** "We use OpenAPI 3 annotations — @Tag for grouping, @Operation for method docs, @Parameter for input specs with format hints, and @Hidden to exclude internal endpoints. This generates interactive Swagger UI docs automatically from the code."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

## SECTION G: Concurrency & Scheduling

---

### G1. SCHEDULING

#### Fixed Cron Job: `@Scheduled`
**File:** [atc/backend/src/main/java/com/lumen/atc/service/MBSPaymentHistoryScheduler.java](atc/backend/src/main/java/com/lumen/atc/service/MBSPaymentHistoryScheduler.java)

```java
@Component
public class MBSPaymentHistoryScheduler {

    @Autowired private MBSServiceAdaptor mbsServiceAdaptor;
    @Autowired private ProjectDataService projectDataService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private WorkflowTransitionService workflowTransitionService;

    @Scheduled(cron = "0 30 2 * * ?")  // Daily at 2:30 AM
    public void fetchPaymentInfo() {
        List<ProjectData> pmtAwaitingProjects = projectDataService.getProjectsByStatuses(projectStatusesList);
        for (ProjectData project : pmtAwaitingProjects) {
            fetchPaymentInfoFromMBS(project.getCustomerId(), project.getProjectDataId(), project.getProjectStatus());
        }
    }
}
```

#### Database-Driven Dynamic Scheduling: `ThreadPoolTaskScheduler` + `CronTrigger`
**File:** [atc/backend/src/main/java/com/lumen/atc/service/ReportSchedulerService.java](atc/backend/src/main/java/com/lumen/atc/service/ReportSchedulerService.java)

```java
@Service
public class ReportSchedulerService {

    private ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("report-scheduler-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(60);
        taskScheduler.initialize();
        refreshSchedules();
    }

    public synchronized void refreshSchedules() {
        cancelAllTasks();
        List<ReportConfig> activeConfigs = reportConfigRepository.findByIsActive("Y");
        for (ReportConfig config : activeConfigs) {
            CronTrigger trigger = new CronTrigger(config.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> reportExecutorService.execute(config), trigger);
            scheduledTasks.put(config.getId(), future);
        }
    }

    @PreDestroy
    public void destroy() {
        cancelAllTasks();
        taskScheduler.shutdown();
    }
}
```

**Explanation:** The codebase uses two scheduling approaches. `@Scheduled` is used for fixed cron jobs that run at compile-time-known intervals. `ThreadPoolTaskScheduler` with `CronTrigger` is used for dynamic scheduling where cron expressions are stored in the database (`REPORT_CONFIG` table) and can be refreshed at runtime via an API call — no application restart needed. The `TaskScheduler` is intentionally NOT registered as a Spring bean (to avoid overriding Spring Boot's auto-configured scheduler used by `@Scheduled`).

**Interview Answer:** "We use @Scheduled for fixed cron jobs and a dedicated ThreadPoolTaskScheduler for database-driven dynamic scheduling. The dynamic scheduler loads cron expressions from REPORT_CONFIG, supports runtime refresh via an API, and is intentionally not a Spring bean to avoid conflicting with @Scheduled processing."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

### G2. THREAD SAFETY

#### Thread-Safety Concern: Mutable Instance Fields
**File:** `mbs-app-API/src/main/java/com/lumen/mbs/batch/service/helper/BatchBillRerunHelper.java`

```java
public class BatchBillRerunHelper {
    // Mutable instance fields — NOT thread-safe
    protected int cntBillComputeDetailDeleted = 0;
    protected int cntBillComputeDeleted = 0;
    protected int cntExpenseComputeDetailDeleted = 0;
    // ... 15 more counters

    protected void resetCounters() {
        cntBillComputeDetailDeleted = 0;
        cntBillComputeDeleted = 0;
        // ...
    }
}
```

**Concern:** `BatchBillRerunHelper` (and its child `BatchBillRerunServiceImpl`) is a Spring `@Service` — a **singleton by default**. The 17 mutable instance fields (counters) are not thread-safe. If `billReRun()` is called concurrently for two different systems, the counters will be corrupted (race conditions). `resetCounters()` at the start of each call doesn't help if another thread is mid-execution. Fix: use `AtomicInteger`, make the counters local variables, or use `@Scope("prototype")`.

#### Thread-Safe Example: ConcurrentHashMap
**File:** [atc/backend/src/main/java/com/lumen/atc/service/ReportSchedulerService.java](atc/backend/src/main/java/com/lumen/atc/service/ReportSchedulerService.java)

```java
private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

public synchronized void refreshSchedules() {
    cancelAllTasks();
    // ... reload and re-register
}

public Map<Long, Boolean> getScheduleStatus() {
    Map<Long, Boolean> status = new ConcurrentHashMap<>();
    scheduledTasks.forEach((id, future) ->
            status.put(id, !future.isCancelled() && !future.isDone()));
    return status;
}
```

**Explanation:** `ReportSchedulerService` uses `ConcurrentHashMap` for the `scheduledTasks` map because `getScheduleStatus()` can be called from a controller thread while `refreshSchedules()` is running on another. The `refreshSchedules()` method is also `synchronized` to prevent concurrent refreshes from creating duplicate schedules. This is proper thread-safety design.

**Interview Answer:** "BatchBillRerunHelper has a thread-safety bug — 17 mutable int counters on a singleton service would corrupt if called concurrently. In contrast, ReportSchedulerService correctly uses ConcurrentHashMap and synchronized to handle concurrent access to its scheduled task registry."

**AUTHORSHIP SIGNAL:** No authorship signal.

---

*End of interview examples document.*


