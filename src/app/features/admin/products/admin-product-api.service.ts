import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable, EMPTY, expand, reduce } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { CsrfService } from '../../../core/auth/csrf.service';
import type {
  AdminProductDetails,
  SaveProductRequest,
  SaveProductResponse,
  AdminProductPage,
  AdminProductSummary,
  ChangeProductVisibilityRequest,
  UpdateProductTranslationsRequest,
  UpdateDigitalProductPriceRequest,
  AdminGarmentVariant,
  AdminGarmentVariantPage,
  UpdateGarmentVariantRequest,
  AdminGarmentVariantFilters,
  AdminEmbroiderySelection,
} from './admin-product.model';

@Injectable({
  providedIn: 'root',
})
export class AdminProductApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  saveProduct(
    productId: string,
    request: SaveProductRequest,
    files: readonly File[],
  ): Observable<SaveProductResponse> {
    return this.csrf.initialize().pipe(
      switchMap(() => {
        const body = new FormData();
        body.append('metadata', new Blob([JSON.stringify(request)], { type: 'application/json' }));
        for (const file of files) body.append('files', file);
        return this.http.post<SaveProductResponse>(
          `${this.baseUrl}/admin/products/${encodeURIComponent(productId)}/edit`,
          body,
        );
      }),
    );
  }

  getProducts(page = 0, size = 20): Observable<AdminProductPage> {
    const params = new HttpParams().set('page', page).set('size', size);

    return this.http.get<AdminProductPage>(`${this.baseUrl}/admin/products`, { params });
  }

  getProduct(id: string): Observable<AdminProductDetails> {
    return this.http.get<AdminProductDetails>(
      `${this.baseUrl}/admin/products/${encodeURIComponent(id)}`,
    );
  }

  updateTranslations(
    id: string,
    request: UpdateProductTranslationsRequest,
  ): Observable<AdminProductDetails> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductDetails>(
            `${this.baseUrl}/admin/products/${encodeURIComponent(id)}/translations`,
            request,
          ),
        ),
      );
  }

  updateDigitalPrice(
    id: string,
    request: UpdateDigitalProductPriceRequest,
  ): Observable<AdminProductDetails> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductDetails>(
            `${this.baseUrl}/admin/products/${encodeURIComponent(id)}/digital-price`,
            request,
          ),
        ),
      );
  }

  getVariants(
    productId: string,
    page = 0,
    size = 20,
    filters: AdminGarmentVariantFilters = {},
  ): Observable<AdminGarmentVariantPage> {
    let params = new HttpParams().set('page', page).set('size', size);

    if (filters.fit) {
      params = params.set('fit', filters.fit);
    }

    if (filters.patternId?.trim()) {
      params = params.set('patternId', filters.patternId.trim());
    }

    if (filters.embroideryOptionId?.trim()) {
      params = params.set('embroideryOptionId', filters.embroideryOptionId.trim());
    }

    return this.http.get<AdminGarmentVariantPage>(
      `${this.baseUrl}/admin/products/${encodeURIComponent(productId)}/variants`,
      { params },
    );
  }

  getEmbroiderySelections(productId: string): Observable<readonly AdminEmbroiderySelection[]> {
    return this.http.get<readonly AdminEmbroiderySelection[]>(
      `${this.baseUrl}/admin/products/${encodeURIComponent(productId)}/variants/embroidery-selections`,
    );
  }

  getVariantMatrix(
    productId: string,
    filters: Required<AdminGarmentVariantFilters>,
  ): Observable<readonly AdminGarmentVariant[]> {
    return this.getVariants(productId, 0, 100, filters).pipe(
      expand((page) =>
        page.page + 1 < page.totalPages
          ? this.getVariants(productId, page.page + 1, 100, filters)
          : EMPTY,
      ),
      reduce(
        (variants, page) => [...variants, ...page.items],
        [] as readonly AdminGarmentVariant[],
      ),
    );
  }

  updateVariant(
    productId: string,
    variantId: string,
    request: UpdateGarmentVariantRequest,
  ): Observable<AdminGarmentVariant> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminGarmentVariant>(
            `${this.baseUrl}/admin/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
            request,
          ),
        ),
      );
  }

  changeVisibility(
    id: string,
    request: ChangeProductVisibilityRequest,
  ): Observable<AdminProductSummary> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductSummary>(
            `${this.baseUrl}/admin/products/${encodeURIComponent(id)}/visibility`,
            request,
          ),
        ),
      );
  }
}
