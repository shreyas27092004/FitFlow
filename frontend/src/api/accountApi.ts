import { baseApi } from './baseApi';

export interface UserProfileDto {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  dateOfBirth?: string;
  roles: string[];
  version: number;
  mustChangePassword?: boolean;
  createdAt: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  phone?: string;
  version: number;
}

export interface DeleteAccountRequest {
  password?: string;
  confirmation: string; // Must be "DELETE"
}

export const accountApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getProfile: builder.query<UserProfileDto, void>({
      query: () => '/me',
      providesTags: ['User'],
    }),

    updateProfile: builder.mutation<UserProfileDto, UpdateProfileRequest>({
      query: (body) => ({
        url: '/me',
        method: 'PATCH',
        body,
      }),
      invalidatesTags: ['User'],
    }),

    exportData: builder.query<any, void>({
      query: () => '/me/export',
    }),

    deleteAccount: builder.mutation<void, DeleteAccountRequest>({
      query: (body) => ({
        url: '/me/delete-request',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['User'],
    }),
  }),
});

export const {
  useGetProfileQuery,
  useUpdateProfileMutation,
  useLazyExportDataQuery,
  useDeleteAccountMutation,
} = accountApi;
