package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.dto.ProductTypeView;
import com.mtfm.gateway.catalog.dto.ProductTypeWriteRequest;
import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import com.mtfm.gateway.catalog.dto.validation.UpdateOp;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 产品类型 REST 接口，前缀 {@code /catalog/product-types}。
 */
@Validated
@RestController
@RequestMapping("/catalog/product-types")
public class CatalogProductTypeController {

    private final CatalogFormService forms;

    public CatalogProductTypeController(CatalogFormService forms) {
        this.forms = forms;
    }

    @GetMapping
    public List<ProductTypeView> listProductTypes() {
        return forms.listProductTypeViews();
    }

    @GetMapping("/{typeId}")
    public ProductTypeView getProductType(@PathVariable @NotBlank String typeId) {
        return forms.requireProductTypeView(typeId);
    }

    @PostMapping
    public ProductTypeView createProductType(
            @Validated(CreateOp.class) @RequestBody ProductTypeWriteRequest request) {
        return forms.toProductTypeView(forms.createProductType(request));
    }

    @PutMapping("/{typeId}")
    public ProductTypeView updateProductType(
            @PathVariable @NotBlank String typeId,
            @Validated(UpdateOp.class) @RequestBody ProductTypeWriteRequest request) {
        return forms.toProductTypeView(forms.updateProductType(typeId, request));
    }

    @DeleteMapping("/{typeId}")
    public Map<String, Object> deleteProductType(@PathVariable @NotBlank String typeId) {
        boolean deleted = forms.deleteProductType(typeId);
        return Map.of("typeId", typeId, "deleted", deleted);
    }
}
