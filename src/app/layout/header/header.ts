import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { shopNavigation } from '../navigation';
import { LanguageSwitcher } from '../language-switcher/language-switcher';
import { MobileMenu } from '../mobile-menu/mobile-menu';
import { CartService } from '../../features/cart/cart.service';


@Component({
  imports: [RouterLink, TranslocoPipe, MobileMenu, LanguageSwitcher],
  selector: 'app-header',
  styleUrl: './header.scss',
  templateUrl: './header.html',
})
export class Header {
protected readonly transloco = inject(TranslocoService);
  protected readonly cart = inject(CartService);
  protected readonly shopNavigation = shopNavigation.filter(
    (item) => item.path !== 'account/login',
  );
}
