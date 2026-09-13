import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WebDesign } from './web-design';

describe('WebDesign', () => {
  let component: WebDesign;
  let fixture: ComponentFixture<WebDesign>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WebDesign, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(WebDesign);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
