import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, map, of, startWith, switchMap, timeout } from 'rxjs';
import { ProductApiService } from './product-api.service';
import type { GarmentVariantApiResponse } from './garment-variant-api.model';

type VariantsState =
  | { status: 'loading' }
  | {
      status: 'ready';
      variants: readonly GarmentVariantApiResponse[];
    }
  | { status: 'error' };

@Injectable({
  providedIn: 'root',
})
export class SweatshirtVariantsService {
  private readonly productApi = inject(ProductApiService);
  private readonly reloadRequests = new Subject<void>();

  readonly state = toSignal(
    this.reloadRequests.pipe(
      startWith(undefined),
      switchMap(() =>
        this.productApi.getVariants('embroidered-sweatshirt').pipe(
          timeout(10_000),
          map((variants): VariantsState => ({
            status: 'ready',
            variants,
          })),
          catchError(() => of<VariantsState>({ status: 'error' })),
          startWith<VariantsState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as VariantsState },
  );

  readonly variants = computed(() => {
    const state = this.state();

    return state.status === 'ready' ? state.variants : [];
  });

  reload(): void {
    this.reloadRequests.next();
  }
}
