import { Routes } from '@angular/router';
import { languageResolver } from './core/i18n/language.resolver';
import { ShopLayout } from './layout/shop-layout/shop-layout';
import { adminGuard } from './core/auth/admin.guard';

const shopRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'products',
    loadComponent: () =>
      import('./features/products/product-list/product-list').then((m) => m.ProductList),
  },
  {
    path: 'services',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/services/services-overview').then((m) => m.ServicesOverview),
  },
  {
    path: 'services/embroidery-digitizing',
    loadComponent: () =>
      import('./features/services/embroidery-digitizing/embroidery-digitizing').then(
        (m) => m.EmbroideryDigitizing,
      ),
  },
  {
    path: 'services/web-design',
    loadComponent: () =>
      import('./features/services/web-design/web-design').then((m) => m.WebDesign),
  },
  {
    path: 'project-inquiry',
    loadComponent: () =>
      import('./features/project-inquiry/project-inquiry').then((m) => m.ProjectInquiry),
  },

  {
    path: 'about',
    loadComponent: () => import('./features/about/about').then((m) => m.About),
  },

  {
    path: 'contact',
    loadComponent: () => import('./features/contact/contact').then((m) => m.Contact),
  },

  {
    path: 'products/:slug',
    loadComponent: () =>
      import('./features/products/product-detail/product-detail').then((m) => m.ProductDetail),
  },
  {
    path: 'cart',
    loadComponent: () => import('./features/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'account/login',
    loadComponent: () => import('./features/auth/customer-login').then((m) => m.CustomerLogin),
  },
  {
    path: 'account/register',
    loadComponent: () =>
      import('./features/auth/customer-register').then((m) => m.CustomerRegister),
  },
  {
    path: 'account/orders',
    loadComponent: () => import('./features/account/customer-orders').then((m) => m.CustomerOrders),
  },
  {
    path: 'account/orders/:id',
    loadComponent: () =>
      import('./features/account/customer-order-details').then((m) => m.CustomerOrderDetails),
  },
  {
    path: 'account/verify-email',
    loadComponent: () =>
      import('./features/auth/customer-email-verification').then(
        (m) => m.CustomerEmailVerification,
      ),
  },
  {
    path: 'account/resend-verification',
    loadComponent: () =>
      import('./features/auth/customer-resend-verification').then(
        (m) => m.CustomerResendVerification,
      ),
  },
  {
    path: 'account/forgot-password',
    loadComponent: () =>
      import('./features/auth/customer-forgot-password').then((m) => m.CustomerForgotPassword),
  },
  {
    path: 'account/reset-password',
    loadComponent: () =>
      import('./features/auth/customer-reset-password').then((m) => m.CustomerResetPassword),
  },
  {
    path: 'account/profile',
    loadComponent: () =>
      import('./features/account/customer-profile').then((m) => m.CustomerProfile),
  },
  {
    path: 'admin/login',
    loadComponent: () =>
      import('./features/auth/admin-login/admin-login').then((m) => m.AdminLogin),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    canActivateChild: [adminGuard],
    loadComponent: () => import('./features/admin/layout/admin-layout').then((m) => m.AdminLayout),
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'orders',
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./features/admin/orders/admin-orders').then((m) => m.AdminOrders),
      },
      {
        path: 'orders/:id',
        loadComponent: () =>
          import('./features/admin/orders/admin-order-details').then((m) => m.AdminOrderDetails),
      },
      {
        path: 'inquiries',
        loadComponent: () =>
          import('./features/admin/inquiries/admin-inquiries').then((m) => m.AdminInquiries),
      },
      {
        path: 'inquiries/:id',
        loadComponent: () =>
          import('./features/admin/inquiries/admin-inquiry-details').then(
            (m) => m.AdminInquiryDetails,
          ),
      },
      {
        path: 'products',
        loadComponent: () =>
          import('./features/admin/products/admin-products').then((m) => m.AdminProducts),
      },
      {
        path: 'products/:id/edit',
        loadComponent: () =>
          import('./features/admin/products/admin-product-edit').then((m) => m.AdminProductEdit),
      },
    ],
  },
  {
    path: 'checkout/payment-result',
    loadComponent: () => import('./features/checkout/payment-result').then((m) => m.PaymentResult),
  },

  {
    path: 'checkout',
    loadComponent: () => import('./features/checkout/checkout').then((m) => m.Checkout),
  },
];

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'pl',
  },
  {
    path: 'pl',
    component: ShopLayout,
    data: { language: 'pl' },
    resolve: { translations: languageResolver },
    children: shopRoutes,
  },
  {
    path: 'en',
    component: ShopLayout,
    data: { language: 'en' },
    resolve: { translations: languageResolver },
    children: shopRoutes,
  },
];
