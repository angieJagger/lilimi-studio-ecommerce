import { Component, inject } from '@angular/core';
import { PRIMARY_OUTLET, Router, RouterLink, UrlSegment } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { shopNavigation } from '../navigation';
import { MobileMenu } from '../mobile-menu/mobile-menu';
import { CartService } from '../../features/cart/cart.service';


@Component({
  imports: [RouterLink, TranslocoPipe, MobileMenu],
  selector: 'app-header',
  styleUrl: './header.scss',
  templateUrl: './header.html',
})
export class Header {
  private readonly router = inject(Router);
  protected readonly transloco = inject(TranslocoService);
  protected readonly cart = inject(CartService);
  protected readonly shopNavigation = shopNavigation;

  protected toggleLanguage(): void {
    const nextLanguage = this.transloco.getActiveLang() === 'pl' ? 'en' : 'pl';

    const url = this.router.parseUrl(this.router.url);
    const primary = url.root.children[PRIMARY_OUTLET];

    if (!primary?.segments.length) {
      return;
    }

    primary.segments[0] = new UrlSegment(nextLanguage, {});

    void this.router.navigateByUrl(url);
  }
}
