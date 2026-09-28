import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Cart } from './cart';
import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { provideSweatshirtVariantsTesting } from '../../testing/sweatshirt-variants-testing';

describe('Cart', () => {
  let component: Cart;
  let fixture: ComponentFixture<Cart>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Cart, getTranslocoTestingModule()],
      providers: [provideRouter([]), provideSweatshirtVariantsTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Cart);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
