import { baseApi } from './baseApi';

export interface ClassDto {
  id: string;
  title: string;
  description: string;
  category: string;
  intensity: 'LOW' | 'MEDIUM' | 'HIGH';
  durationMinutes: number;
  capacityDefault: number;
  active: boolean;
  trainerName?: string;
  tags?: string[];
}

export interface SessionDto {
  id: string;
  classId: string;
  classTitle: string;
  category: string;
  intensity: 'LOW' | 'MEDIUM' | 'HIGH';
  trainerId: string;
  trainerName: string;
  roomName: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  bookedCount: number;
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  safetyVerdict?: 'ALLOWED' | 'CAUTION' | 'RESTRICTED' | 'RED_FLAG';
}

export interface ClassesMetadata {
  categories: string[];
  intensities: string[];
  rooms: string[];
}

export const classesApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getClasses: builder.query<{ content: ClassDto[]; totalElements: number }, { page?: number; size?: number; category?: string; search?: string } | void>({
      query: (params) => ({
        url: '/classes',
        params: params || undefined,
      }),
      providesTags: ['Classes'],
    }),

    getClassById: builder.query<ClassDto, string>({
      query: (id) => `/classes/${id}`,
      providesTags: (_res, _err, id) => [{ type: 'Classes', id }],
    }),

    getClassesMetadata: builder.query<ClassesMetadata, void>({
      query: () => '/classes/metadata',
    }),

    getSessions: builder.query<{ content: SessionDto[]; totalElements: number }, { page?: number; size?: number; from?: string; to?: string; category?: string; classId?: string } | void>({
      query: (params) => ({
        url: '/sessions',
        params: params || undefined,
      }),
      providesTags: ['Sessions'],
    }),

    getSessionById: builder.query<SessionDto, string>({
      query: (id) => `/sessions/${id}`,
      providesTags: (_res, _err, id) => [{ type: 'Sessions', id }],
    }),
  }),
});

export const {
  useGetClassesQuery,
  useGetClassByIdQuery,
  useGetClassesMetadataQuery,
  useGetSessionsQuery,
  useGetSessionByIdQuery,
} = classesApi;
