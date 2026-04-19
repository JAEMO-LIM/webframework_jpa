package kr.ac.hansung.cse.controller;

import jakarta.validation.Valid;
import kr.ac.hansung.cse.exception.ProductNotFoundException;
import kr.ac.hansung.cse.model.Product;
import kr.ac.hansung.cse.model.ProductForm;
import kr.ac.hansung.cse.service.CategoryService;
import kr.ac.hansung.cse.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * =====================================================================
 * ProductController - 웹 요청 처리 계층 (Controller Layer)
 * =====================================================================
 *
 * MVC 패턴에서 Controller의 역할:
 *   1. HTTP 요청을 받아 적절한 Service 메서드를 호출합니다.
 *   2. Service로부터 받은 결과를 Model에 담아 View에 전달합니다.
 *   3. 어떤 View를 렌더링할지 결정하여 뷰 이름을 반환합니다.
 *
 * [엔드포인트 목록]
 * GET  /products          → 상품 목록
 * GET  /products/{id}     → 상품 상세
 * GET  /products/create   → 상품 등록 폼
 * POST /products/create   → 상품 등록 처리
 * GET  /products/{id}/edit  → 상품 수정 폼
 * POST /products/{id}/edit  → 상품 수정 처리
 * POST /products/{id}/delete → 상품 삭제 처리
 */
@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    public ProductController(ProductService productService, CategoryService categoryService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }


    // ─────────────────────────────────────────────────────────────────
    // GET /products - 상품 목록 조회
    // ─────────────────────────────────────────────────────────────────



    // ─────────────────────────────────────────────────────────────────
    // GET /products/{id} - 상품 상세 조회
    // ─────────────────────────────────────────────────────────────────

    /**
     * @PathVariable : URL 경로의 변수를 메서드 파라미터로 바인딩합니다.
     *                 예) GET /products/1 → id = 1L
     *
     * ProductNotFoundException: 커스텀 예외를 사용합니다.
     *   → GlobalExceptionHandler.handleProductNotFound()가 처리합니다.
     */
    @GetMapping("/{id}")
    public String showProduct(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        model.addAttribute("product", product);
        return "productView";
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /products/create - 상품 등록 폼 표시
    // ─────────────────────────────────────────────────────────────────

    /**
     * 빈 ProductForm 객체를 Model에 담아 폼을 표시합니다.
     *
     * [ProductForm DTO를 사용하는 이유]
     * 1. Bean Validation 어노테이션을 엔티티가 아닌 DTO에 적용합니다.
     * 2. JPA 엔티티의 보호 생성자 문제를 우회합니다.
     * 3. 외부에서 수정 불가한 필드(id 등)를 폼에서 분리합니다.
     *
     * Model attribute 이름: "productForm"
     *   → Thymeleaf에서 th:object="${productForm}"으로 접근합니다.
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        return "productForm";
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /products/create - 상품 등록 처리
    // ─────────────────────────────────────────────────────────────────

    /**
     * @Valid: productForm에 선언된 Bean Validation 어노테이션을 실행합니다.
     *         (@NotBlank, @NotNull, @DecimalMin 등)
     *
     * @ModelAttribute("productForm") ProductForm productForm:
     *   - HTTP POST 요청 파라미터를 ProductForm 객체에 자동 바인딩합니다.
     *   - "productForm" 이름으로 Model에 자동 등록됩니다.
     *   - @Valid에 의해 검증이 수행됩니다.
     *
     * BindingResult bindingResult:
     *   - 검증 결과(오류 목록)를 담는 객체입니다.
     *   - 반드시 @ModelAttribute 파라미터 바로 다음에 위치해야 합니다.
     *   - BindingResult가 없으면 검증 실패 시 MethodArgumentNotValidException 발생
     *   - BindingResult가 있으면 오류를 직접 처리할 수 있습니다.
     *
     * [처리 흐름]
     * ① Spring MVC가 폼 파라미터 → ProductForm 바인딩
     * ② @Valid에 의해 Bean Validation 실행
     * ③ bindingResult.hasErrors()로 오류 확인
     *   - 오류 있음 → 폼 뷰로 돌아감 (오류 메시지 표시)
     *   - 오류 없음 → 서비스 호출 → 리다이렉트 (PRG 패턴)
     */
    @PostMapping("/create")
    public String createProduct(@Valid @ModelAttribute("productForm") ProductForm productForm,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {

        // 검증 오류가 있으면 폼을 다시 표시합니다.
        // bindingResult는 productForm과 함께 Model에 자동으로 포함되므로
        // Thymeleaf에서 th:errors로 오류 메시지에 접근할 수 있습니다.
        if (bindingResult.hasErrors()) {
            return "productForm"; // 오류가 있는 채로 폼 뷰 재표시
        }

        // 검증 통과: ProductForm → Product 엔티티 변환 후 저장
        // category 이름으로 Category 엔티티를 조회하여 연결합니다.
        Product product = productForm.toEntity();
        product.setCategory(productService.resolveCategory(productForm.getCategory()));
        Product savedProduct = productService.createProduct(product);

        // Flash 속성: 리다이렉트 후 한 번만 표시되는 메시지
        redirectAttributes.addFlashAttribute("successMessage",
                "'" + savedProduct.getName() + "' 상품이 성공적으로 등록되었습니다.");

        // PRG 패턴: POST → Redirect → GET (중복 제출 방지)
        return "redirect:/products";
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /products/{id}/edit - 상품 수정 폼 표시
    // ─────────────────────────────────────────────────────────────────

    /**
     * 기존 상품 데이터를 조회하여 수정 폼에 채워 표시합니다.
     * ProductForm.from(product)으로 엔티티 → DTO 변환합니다.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        // 엔티티 → DTO 변환 (기존 데이터로 폼 초기화)
        model.addAttribute("productForm", ProductForm.from(product));
        return "productEditForm";
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /products/{id}/edit - 상품 수정 처리
    // ─────────────────────────────────────────────────────────────────

    /**
     * [JPA 엔티티 수정 방식 설명]
     *
     * getProductById()는 readOnly 트랜잭션에서 실행됩니다.
     * 반환된 Product 엔티티는 트랜잭션 종료 후 "준영속(Detached) 상태"가 됩니다.
     *
     * 준영속 상태의 특징:
     *   - 영속성 컨텍스트가 관리하지 않습니다.
     *   - setter를 호출해도 DB에 반영되지 않습니다.
     *   - merge()를 통해 다시 영속 상태로 만들 수 있습니다.
     *
     * updateProduct()에서 EntityManager.merge(product)를 호출하면:
     *   - 새 트랜잭션이 시작됩니다.
     *   - Hibernate가 동일 ID의 레코드를 DB에서 SELECT합니다.
     *   - 준영속 엔티티의 변경된 값을 관리 엔티티에 복사합니다.
     *   - 트랜잭션 커밋 시 UPDATE SQL이 자동 실행됩니다.
     */
    @PostMapping("/{id}/edit")
    public String updateProduct(@PathVariable Long id,
                                @Valid @ModelAttribute("productForm") ProductForm productForm,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "productEditForm"; // 오류가 있는 채로 수정 폼 재표시
        }

        // 기존 엔티티 조회 (준영속 상태로 반환됨)
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        // 폼 데이터로 엔티티 필드를 업데이트합니다.
        product.setName(productForm.getName());
        product.setCategory(productService.resolveCategory(productForm.getCategory()));
        product.setPrice(productForm.getPrice());
        product.setDescription(productForm.getDescription());

        // merge()를 통해 준영속 엔티티의 변경사항을 DB에 반영합니다.
        productService.updateProduct(product);

        redirectAttributes.addFlashAttribute("successMessage",
                "'" + product.getName() + "' 상품 정보가 수정되었습니다.");
        return "redirect:/products/" + id;
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /products/{id}/delete - 상품 삭제 처리
    // ─────────────────────────────────────────────────────────────────

    /**
     * HTML 폼은 GET/POST만 지원하므로 DELETE 대신 POST를 사용합니다.
     * (REST API에서는 HTTP DELETE 메서드를 사용하는 것이 표준입니다.)
     *
     * 삭제 전 상품 이름을 미리 조회하여 성공 메시지에 포함합니다.
     * 삭제 후에는 상세 페이지로 돌아갈 수 없으므로 목록으로 리다이렉트합니다.
     */
    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {

        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        String productName = product.getName(); // 삭제 전 이름 저장
        productService.deleteProduct(id);

        redirectAttributes.addFlashAttribute("successMessage",
                "'" + productName + "' 상품이 삭제되었습니다.");
        return "redirect:/products";
    }

    @GetMapping
    public String listProducts(
            @RequestParam(required = false) String keyword,   // GET ?keyword=노트북
            @RequestParam(required = false) Long categoryId,  // GET ?categoryId=1
            Model model) {
        List<Product> products;
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = categoryId != null;

        if (hasKeyword && hasCategory) {
            products = productService.searchByNameAndCategory(keyword, categoryId);
        } else if (hasKeyword) {
            products = productService.searchByName(keyword);
        } else if (hasCategory) {
            products = productService.searchByCategory(categoryId);
        } else {
            products = productService.getAllProducts();
        }
        model.addAttribute("products", products);
        // 카테고리 드롭다운 목록 + 현재 검색 조건 유지
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);    return "productList"; }

}
