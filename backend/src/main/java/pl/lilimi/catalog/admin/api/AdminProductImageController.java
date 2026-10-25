package pl.lilimi.catalog.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import pl.lilimi.catalog.admin.application.AdminProductImageUploadService;
import pl.lilimi.catalog.admin.application.AdminProductImageQueryService;
import pl.lilimi.catalog.admin.application.AdminProductImageTextService;
import pl.lilimi.catalog.admin.application.AdminProductImageContentService;
import pl.lilimi.catalog.admin.application.AdminProductPrimaryImageService;
import pl.lilimi.catalog.admin.application.AdminProductImageOrderService;
import pl.lilimi.catalog.admin.application.AdminProductImageDeleteService;
import pl.lilimi.catalog.admin.application.AdminProductImageReplaceService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
public class AdminProductImageController {

  private final AdminProductImageQueryService queryService;
  private final AdminProductImageTextService textService;
  private final AdminProductImageUploadService uploadService;
  private final AdminProductImageContentService contentService;
  private final AdminProductPrimaryImageService primaryImageService;
  private final AdminProductImageOrderService orderService;
  private final AdminProductImageDeleteService deleteService;
  private final AdminProductImageReplaceService replaceService;

  public AdminProductImageController(
    AdminProductImageQueryService queryService,
    AdminProductImageTextService textService,
    AdminProductImageUploadService uploadService,
    AdminProductImageContentService contentService,
    AdminProductPrimaryImageService primaryImageService,
    AdminProductImageOrderService orderService,
    AdminProductImageDeleteService deleteService,
    AdminProductImageReplaceService replaceService
  ) {
    this.queryService = queryService;
    this.textService = textService;
    this.uploadService = uploadService;
    this.contentService = contentService;
    this.primaryImageService = primaryImageService;
    this.orderService = orderService;
    this.deleteService = deleteService;
    this.replaceService = replaceService;
  }

  @GetMapping
  public AdminProductImagesResponse findImages(
    @PathVariable String productId
  ) {
    return queryService.findImages(productId);
  }

  @PatchMapping("/{imageId}/text")
  public AdminProductImagesResponse updateText(
    @PathVariable String productId,
    @PathVariable UUID imageId,
    @Valid @RequestBody UpdateProductImageTextRequest request
  ) {
    return textService.updateText(productId, imageId, request);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AdminProductImagesResponse upload(
    @PathVariable String productId,
    @RequestPart("file") MultipartFile file,
    @Valid @RequestPart("metadata") UploadProductImageRequest request
  ) {
    return uploadService.upload(productId, file, request);
  }

  @GetMapping("/{imageId}/content")
  public ResponseEntity<Resource> loadContent(
    @PathVariable String productId,
    @PathVariable UUID imageId
  ) {
    var content = contentService.load(productId, imageId);

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(content.contentType()))
      .contentLength(content.byteSize())
      .cacheControl(CacheControl.noStore())
      .header("X-Content-Type-Options", "nosniff")
      .body(content.resource());
  }

  @PatchMapping("/{imageId}/primary")
  public AdminProductImagesResponse changePrimaryImage(
    @PathVariable String productId,
    @PathVariable UUID imageId,
    @Valid @RequestBody ChangePrimaryProductImageRequest request
  ) {
    return primaryImageService.changePrimaryImage(
      productId,
      imageId,
      request
    );
  }

  @PatchMapping("/order")
  public AdminProductImagesResponse reorder(
    @PathVariable String productId,
    @Valid @RequestBody ReorderProductImagesRequest request
  ) {
    return orderService.reorder(productId, request);
  }

  @DeleteMapping("/{imageId}")
  public AdminProductImagesResponse delete(
    @PathVariable String productId,
    @PathVariable UUID imageId,
    @RequestParam @PositiveOrZero long expectedVersion
  ) {
    return deleteService.delete(
      productId,
      imageId,
      expectedVersion
    );
  }

  @PostMapping(
    value = "/{imageId}/file",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public AdminProductImagesResponse replaceFile(
    @PathVariable String productId,
    @PathVariable UUID imageId,
    @RequestPart("file") MultipartFile file,
    @Valid @RequestPart("metadata") ReplaceProductImageRequest request
  ) {
    return replaceService.replace(
      productId,
      imageId,
      file,
      request
    );
  }
}
