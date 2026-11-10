import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-base-url';
import type { ProductApiResponse } from './product-api.model';
import type { GarmentVariantApiResponse } from './garment-variant-api.model';

@Injectable({
  providedIn: 'root',
})
export class ProductApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getProducts(): Observable<readonly ProductApiResponse[]> {
    return this.http.get<readonly ProductApiResponse[]>(`${this.baseUrl}/products`);
  }

  getProduct(slug: string): Observable<ProductApiResponse> {
    return this.http.get<ProductApiResponse>(
      `${this.baseUrl}/products/${encodeURIComponent(slug)}`,
    );
  }

  checkQuantity(slug: string, quantity: number, variantId?: string): Observable<{ maxQuantity: number } | null> {
    return this.http.get<{ maxQuantity: number } | null>(
      `${this.baseUrl}/products/${encodeURIComponent(slug)}/quantity-check`,
      { params: variantId ? { quantity, variantId } : { quantity }, transferCache: false },
    );
  }

  getVariants(slug: string): Observable<readonly GarmentVariantApiResponse[]> {
    return this.http.get<readonly GarmentVariantApiResponse[]>(
      `${this.baseUrl}/products/${encodeURIComponent(slug)}/variants`,
    );
  }
}
