package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.apply.CatalogApplyService;
import com.mtfm.gateway.catalog.dto.FunctionFormView;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.dto.ProductFunctionView;
import com.mtfm.gateway.catalog.dto.ProductFunctionWriteRequest;
import com.mtfm.gateway.catalog.dto.ProductListQuery;
import com.mtfm.gateway.catalog.dto.ProductView;
import com.mtfm.gateway.catalog.dto.ProductWriteRequest;
import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import com.mtfm.gateway.catalog.dto.validation.UpdateOp;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 产品与功能 REST 接口，前缀 {@code /catalog/products}。
 *
 * <p>示例：{@code GET /catalog/products/{productId}/functions} 列出产品功能；
 * {@code POST /catalog/products} 创建产品。
 */
@Validated
@RestController
@RequestMapping("/catalog/products")
public class CatalogProductController {

    private final CatalogApplyService applyService;
    private final CatalogFormService forms;

    public CatalogProductController(CatalogApplyService applyService, CatalogFormService forms) {
        this.applyService = applyService;
        this.forms = forms;
    }

    @GetMapping
    public PageResult<ProductView> listProducts(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1") @Max(value = 100, message = "size 不能超过 100") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String productTypeId) {
        return forms.pageProductViews(page, size, new ProductListQuery(name, code, productTypeId));
    }

    @GetMapping("/{productId}")
    public ProductView getProduct(@PathVariable String productId) {
        return forms.requireProductView(productId);
    }

    @PostMapping
    public ProductView createProduct(@Validated(CreateOp.class) @RequestBody ProductWriteRequest request) {
        return forms.toProductView(forms.createProduct(request));
    }

    @PutMapping("/{productId}")
    public ProductView updateProduct(@PathVariable String productId,
            @Validated(UpdateOp.class) @RequestBody ProductWriteRequest request) {
        return forms.toProductView(forms.updateProduct(productId, request));
    }

    @DeleteMapping("/{productId}")
    public Map<String, Object> deleteProduct(@PathVariable String productId) {
        boolean deleted = forms.deleteProduct(productId);
        return Map.of("productId", productId, "deleted", deleted);
    }

    @GetMapping("/{productId}/functions")
    public List<ProductFunctionView> listProductFunctions(@PathVariable String productId) {
        return forms.listProductFunctionViews(productId);
    }

    @GetMapping("/{productId}/function-forms")
    public List<FunctionFormView> productFunctionForms(@PathVariable String productId) {
        return forms.productFunctions(productId);
    }

    @GetMapping("/{productId}/functions/{functionId}")
    public FunctionFormView productFunction(@PathVariable String productId, @PathVariable String functionId) {
        return forms.productFunction(productId, functionId);
    }

    @PostMapping("/{productId}/functions")
    public ProductFunctionView createFunction(@PathVariable String productId,
            @Validated(CreateOp.class) @RequestBody ProductFunctionWriteRequest request) {
        ProductFunctionView view = forms.toProductFunctionView(forms.createFunction(productId, request));
        applyService.refreshSchedulesForProduct(productId);
        return view;
    }

    @PostMapping("/{productId}/functions/import")
    public List<ProductFunctionView> importFunctions(@PathVariable String productId,
            @RequestParam @NotBlank(message = "capabilityType 不能为空") String capabilityType) {
        List<ProductFunctionView> views = forms.importCapabilityFunctions(productId, capabilityType).stream()
                .map(forms::toProductFunctionView)
                .toList();
        applyService.refreshSchedulesForProduct(productId);
        return views;
    }

    @PutMapping("/{productId}/functions/{functionId}")
    public ProductFunctionView updateFunction(@PathVariable String productId, @PathVariable String functionId,
            @Validated(UpdateOp.class) @RequestBody ProductFunctionWriteRequest request) {
        ProductFunctionView view = forms.toProductFunctionView(forms.updateFunction(productId, functionId, request));
        applyService.refreshSchedulesForProduct(productId);
        return view;
    }

    @DeleteMapping("/{productId}/functions/{functionId}")
    public Map<String, Object> deleteFunction(@PathVariable String productId, @PathVariable String functionId) {
        boolean deleted = forms.deleteFunction(productId, functionId);
        applyService.refreshSchedulesForProduct(productId);
        return Map.of("productId", productId, "functionId", functionId, "deleted", deleted);
    }
}
