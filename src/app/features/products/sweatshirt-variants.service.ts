import { computed, inject, Injectable, Injector, signal } from '@angular/core';
import type { Signal } from '@angular/core';
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

  export interface VariantsSource {
    readonly state: Signal<VariantsState>;
    readonly variants: Signal<readonly GarmentVariantApiResponse[]>;
    readonly reload: () => void;
  }

@Injectable({
  providedIn: 'root',
})
export class SweatshirtVariantsService {
  private readonly productApi = inject(ProductApiService);
  private readonly injector = inject(Injector);
  private readonly sources = signal<ReadonlyMap<string, VariantsSource>>(new Map());

  findSource(slug: string): VariantsSource | undefined {
    return this.sources().get(slug);
  }

  forProduct(slug: string): VariantsSource {
    const existing = this.findSource(slug);
    if (existing) {
      return existing;
    }

    const reloadRequests = new Subject<void>();
    const state = toSignal(
      reloadRequests.pipe(
        startWith(undefined),
        switchMap(() =>
          this.productApi.getVariants(slug).pipe(
            timeout(10_000),
            map((variants): VariantsState => ({ status: 'ready', variants })),
            catchError(() => of<VariantsState>({ status: 'error' })),
            startWith<VariantsState>({ status: 'loading' }),
          ),
        ),
      ),
      {
        injector: this.injector,
        initialValue: { status: 'loading' } as VariantsState,
      },
    );

    const source: VariantsSource = {
      state,
      variants: computed(() => {
        const current = state();
        return current.status === 'ready' ? current.variants : [];
      }),
      reload: () => reloadRequests.next(),
    };

    this.sources.update((current) => new Map(current).set(slug, source));
    return source;
  }
}
