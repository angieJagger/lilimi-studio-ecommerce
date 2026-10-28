import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductCatalogService } from '../products/product-catalog.service';
import { createProductCatalogMock } from '../../testing/product-catalog-testing';
import { Home } from './home';
import { provideProductCatalogTesting } from '../../testing/product-catalog-testing';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home, getTranslocoTestingModule()],
      providers: [provideRouter([]), provideProductCatalogTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should link each whole service card to its matching service section', () => {
    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('a.offer-card'),
    ) as HTMLAnchorElement[];
    expect(cards).toHaveLength(3);
    for (const card of cards) {
      const href = card.getAttribute('href')!;
      expect(href).toMatch(/^\/pl\/services#service-/);
      expect(card.querySelector('h3')).not.toBeNull();
      expect(card.querySelector('a')).toBeNull();
    }
    expect(new Set(cards.map((card) => card.hash)).size).toBe(3);
  });
  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should refresh a previously loaded catalog when returning to the home page', () => {
    const catalog = TestBed.inject(ProductCatalogService) as unknown as ReturnType<
      typeof createProductCatalogMock
    >;
    expect(catalog.reload).toHaveBeenCalledTimes(1);
  });

  it('should pass the featured product primary image to its card', async () => {
    const catalog = TestBed.inject(ProductCatalogService) as unknown as ReturnType<
      typeof createProductCatalogMock
    >;
    catalog.state.set({
      status: 'ready',
      products: catalog
        .products()
        .map((product) =>
          product.id === 'pattern-001'
            ? {
                ...product,
                images: [
                  {
                    id: 'dragon-photo',
                    width: 700,
                    height: 600,
                    primaryImage: true,
                    alt: { pl: 'Smok', en: 'Dragon' },
                    version: 3,
                  },
                ],
              }
            : product,
        ),
    });
    await fixture.whenStable();
    const image = fixture.nativeElement.querySelector('app-product-card img') as HTMLImageElement;
    expect(image.getAttribute('src')).toBe(
      '/api/products/forest-dragon/images/dragon-photo/content?v=3',
    );
    expect(image.alt).toBe('Smok');
  });
});
