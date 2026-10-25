import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductCard } from './product-card';

describe('ProductCard', () => {
  let component: ProductCard;
  let fixture: ComponentFixture<ProductCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductCard],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductCard);

    fixture.componentRef.setInput('productName', 'Wzór haftu „Leśny smok”');
    fixture.componentRef.setInput('typeLabel', 'Plik cyfrowy');
    fixture.componentRef.setInput('priceInGrosz', 2900);

    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show only the primary image on the card', async () => {
    const photo = {
      id: 'secondary',
      width: 400,
      height: 300,
      primaryImage: false,
      alt: { pl: 'Drugie', en: 'Second' },
      version: 2,
    };
    fixture.componentRef.setInput('slug', 'forest-dragon');
    fixture.componentRef.setInput('images', [
      photo,
      { ...photo, id: 'primary', primaryImage: true },
    ]);
    await fixture.whenStable();
    const images = fixture.nativeElement.querySelectorAll('img');
    expect(images).toHaveLength(1);
    expect(images[0].getAttribute('src')).toContain('/forest-dragon/images/primary/');
  });
});
