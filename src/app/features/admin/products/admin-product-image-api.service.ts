import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { CsrfService } from '../../../core/auth/csrf.service';
import type {
  AdminProductImages,
  ChangePrimaryProductImageRequest,
  ReorderProductImagesRequest,
  ReplaceProductImageRequest,
  UpdateProductImageTextRequest,
  UploadProductImageRequest,
} from './admin-product-image.model';

@Injectable({
  providedIn: 'root',
})
export class AdminProductImageApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getImages(productId: string): Observable<AdminProductImages> {
    return this.http.get<AdminProductImages>(this.imagesUrl(productId));
  }

  getImageContent(productId: string, imageId: string): Observable<Blob> {
    return this.http.get(`${this.imageUrl(productId, imageId)}/content`, { responseType: 'blob' });
  }

  upload(
    productId: string,
    file: File,
    request: UploadProductImageRequest,
  ): Observable<AdminProductImages> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.post<AdminProductImages>(
            this.imagesUrl(productId),
            this.multipartBody(file, request),
          ),
        ),
      );
  }

  replaceFile(
    productId: string,
    imageId: string,
    file: File,
    request: ReplaceProductImageRequest,
  ): Observable<AdminProductImages> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.post<AdminProductImages>(
            `${this.imageUrl(productId, imageId)}/file`,
            this.multipartBody(file, request),
          ),
        ),
      );
  }

  updateText(
    productId: string,
    imageId: string,
    request: UpdateProductImageTextRequest,
  ): Observable<AdminProductImages> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductImages>(`${this.imageUrl(productId, imageId)}/text`, request),
        ),
      );
  }

  changePrimaryImage(
    productId: string,
    imageId: string,
    request: ChangePrimaryProductImageRequest,
  ): Observable<AdminProductImages> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductImages>(
            `${this.imageUrl(productId, imageId)}/primary`,
            request,
          ),
        ),
      );
  }

  reorder(productId: string, request: ReorderProductImagesRequest): Observable<AdminProductImages> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductImages>(`${this.imagesUrl(productId)}/order`, request),
        ),
      );
  }

  delete(
    productId: string,
    imageId: string,
    expectedVersion: number,
  ): Observable<AdminProductImages> {
    const params = new HttpParams().set('expectedVersion', expectedVersion);

    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.delete<AdminProductImages>(this.imageUrl(productId, imageId), { params }),
        ),
      );
  }

  private imagesUrl(productId: string): string {
    return `${this.baseUrl}/admin/products/${encodeURIComponent(productId)}/images`;
  }

  private imageUrl(productId: string, imageId: string): string {
    return `${this.imagesUrl(productId)}/${encodeURIComponent(imageId)}`;
  }

  private multipartBody(
    file: File,
    metadata: UploadProductImageRequest | ReplaceProductImageRequest,
  ): FormData {
    const body = new FormData();

    body.append('file', file);
    body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));

    return body;
  }
}
