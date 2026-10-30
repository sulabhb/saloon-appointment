package com.salonbooking.catalog.web;

import com.salonbooking.catalog.app.CatalogAdminService;
import com.salonbooking.catalog.app.CatalogAdminService.CategoryView;
import com.salonbooking.catalog.app.CatalogAdminService.ServiceInput;
import com.salonbooking.identity.app.StaffPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The service menu. Every change returns the whole catalogue so the console can redraw it. */
@RestController
@RequestMapping("/api/v1/admin")
class CatalogAdminController {

    record CategoryRequest(@NotBlank @Size(max = 80) String name) {}

    record ServiceRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @Min(5) @Max(600) int durationMin,
            @Min(0) @Max(120) int bufferAfterMin,
            @PositiveOrZero long priceCents,
            boolean priceIsFrom,
            @Size(max = 300) String customerNotice,
            Boolean active,
            List<UUID> offerAtLocationIds) {

        ServiceInput input() {
            return new ServiceInput(categoryId, name, description, durationMin, bufferAfterMin, priceCents, priceIsFrom,
                    customerNotice);
        }
    }

    private final CatalogAdminService catalog;

    CatalogAdminController(CatalogAdminService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    List<CategoryView> catalog() {
        return catalog.catalog();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    List<CategoryView> createCategory(@AuthenticationPrincipal StaffPrincipal staff,
            @Valid @RequestBody CategoryRequest request) {
        catalog.createCategory(staff, request.name());
        return catalog.catalog();
    }

    @PutMapping("/categories/{categoryId}")
    List<CategoryView> renameCategory(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryRequest request) {
        catalog.renameCategory(staff, categoryId, request.name());
        return catalog.catalog();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    List<CategoryView> createService(@AuthenticationPrincipal StaffPrincipal staff,
            @Valid @RequestBody ServiceRequest request) {
        catalog.createService(staff, request.input(),
                request.offerAtLocationIds() == null ? List.of() : request.offerAtLocationIds());
        return catalog.catalog();
    }

    @PutMapping("/services/{serviceId}")
    List<CategoryView> updateService(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceRequest request) {
        catalog.updateService(staff, serviceId, request.input(), request.active() == null || request.active());
        return catalog.catalog();
    }
}
