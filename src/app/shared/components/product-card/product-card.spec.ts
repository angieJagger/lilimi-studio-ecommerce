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
});
