// Shared presentation data for the home and service overview pages.
// Project types match the existing inquiry API; stores and maintenance use "other".
export const primaryStudioServices = [
  { id: 'websites', projectType: 'website' },
  { id: 'stores', projectType: 'other' },
  { id: 'maintenance', projectType: 'other' },
] as const;

export const additionalStudioServices = [
  { id: 'design', projectType: 'uxUi' },
  { id: 'graphics', projectType: 'graphicDesign' },
  { id: 'digitizing', projectType: 'digitizing' },
] as const;
