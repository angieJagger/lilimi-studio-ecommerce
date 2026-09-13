import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { shopNavigation } from '../../layout/navigation';
import { ProductCard } from '../../shared/components/product-card/product-card';

@Component({
  imports: [TranslocoPipe, RouterLink, ProductCard],
  selector: 'app-home',
  styleUrl: './home.scss',
  templateUrl: './home.html',
})
export class Home {
  protected readonly transloco = inject(TranslocoService);
  protected readonly offerItems = shopNavigation;
}
