package com.ordenaris.restaurant

import grails.gorm.transactions.Transactional
import com.ordenaris.security.User
import com.ordenaris.restaurant.Dish
import com.ordenaris.order.*
import com.ordenaris.Log
import com.ordenaris.TypeError
import com.ordenaris.restaurant.EntityStatus

@Transactional
class ReviewService {
    def mapReview = { Review review ->
        def obj = 
        [
            uuid: review.uuid,
            user: [
                username: review.user?.username,
            ],
            comment: review.comment,
            rating: review.rating,
            dateCreated: review.dateCreated.getTime(),
        ]
        
    }
    def listReviews(dishUuid, page, max, rating, logId) {
        try {
            Log.logger(Log.INFO, logId, "Listado de Resenias.", "Llegada al servicio.", "params: { dish: ${dishUuid}, rating: ${rating} }")
            def dish = Dish.findByUuid(dishUuid)
            if (!dish) {
                Log.logger(Log.WARN, logId, "Listado de Resenias.", "Platillo no encontrado.", "params: { dish: ${dishUuid}, rating: ${rating} }")
                return TypeError.informationNotFound(logId)
            }
            Integer offset = page * max - max
            def reviews = Review.createCriteria().list {
                eq("dish", dish)
                eq("status", EntityStatus.ACTIVE)
                if(rating){
                    eq("rating", rating as Float)
                }
                firstResult(offset)
                maxResults(max)
                order("dateCreated", "desc")
            }

            if(reviews.isEmpty()) {
                Log.logger(Log.INFO, logId, "Listado de Resenias.", "Platillo sin resenias.", "params: { dish: ${dishUuid}, rating: ${rating} }")
                return [
                    data: [success: true, data: [dish: [message: 'No hay reseñas para listar', dishName: dish.name, dishUuid: dish.uuid], reviews: []]],
                    status: 200
                ]
            }else {
                def reviewsMapper = reviews.collect { review -> mapReview(review) }
                Log.logger(Log.INFO, logId, "Listado de Resenias.", "Resenias devueltas de manera exitosa.", "params: { dish: ${dishUuid}, rating: ${rating} }", "Reseñas: ${reviews.size()}")
                return [
                    data: [success: true, data: [dish: [message: 'Reseñas listadas', dishName: reviews[0].dish.name, dishUuid: reviews[0].dish.uuid], reviews: reviewsMapper]],
                    status: 200
                ]
            }
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Listado de Resenias.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
    def countTotalReviews(dishUuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Conteo de Resenias totales.", "Llegada al servicio.", "dish: $dishUuid")
            def dish = Dish.findByUuid(dishUuid)
            if (!dish) {
                Log.logger(Log.WARN, logId, "Conteo de Resenias totales.", "Platillo no encontrado.", "dish: $dishUuid")
                return TypeError.informationNotFound(logId)
            }
            def totalReviews = Review.countByDishAndStatus(dish, EntityStatus.ACTIVE)
            Log.logger(Log.INFO, logId, "Conteo de Resenias totales.", "Conteo realizado exitosamente.", "dish: $dishUuid", "totalReviews: $totalReviews")
            return [data: [success: true, data: totalReviews], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Conteo de Resenias totales.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
    def statisticsDish(dishUuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Estadisticas Platillo.", "Llegada al servicio.", "dish: ${dishUuid}")
            def totalReviews = Review.createCriteria().count {
                dish {
                    eq("uuid", dishUuid)
                }
                eq("status", EntityStatus.ACTIVE)
            }
            def avgRating = Review.createCriteria().get {
                dish {
                    eq("uuid", dishUuid)
                }
                eq("status", EntityStatus.ACTIVE)
                projections {
                    avg("rating")
                }
            } ?: 0
            avgRating = avgRating ? avgRating.round(1) : 0
            def ratingsBreakdown = [:]
            (1..5).each { rating ->
                ratingsBreakdown[rating] = Review.createCriteria().count {
                    dish {
                        eq("uuid", dishUuid)
                    }
                    eq("status", EntityStatus.ACTIVE)
                    eq("rating", rating as Float)
                }
            }
            Log.logger(Log.INFO, logId, "Estadisticas Platillo.", "Estadisticas obtenidas correctamente.", "dish: ${dishUuid}", "Promedio: ${avgRating}, Total: ${totalReviews}, Puntuaciones: ${ratingsBreakdown}")
            return [
                data: [
                    success: true,
                    data: [
                        message: 'Reseñas obtenidas correctamente',
                        stats: [
                            averageRating: avgRating,
                            totalReviews: totalReviews,
                            ratings: ratingsBreakdown
                        ]
                    ]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Estadisticas Platillo.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
    def createReview(data, auth, logId) {
        try {
            Log.logger(Log.INFO, logId, "Crear Reseña.", "Llegada al servicio.", "data: ${data}")
            if (!auth) {
                Log.logger(Log.WARN, logId, "Crear Reseña.", "No hay un usuario.", "data: ${data}")
                return TypeError.noPermissions(logId)
            }
            def dish = Dish.findByUuid(data.dishUuid)
            if (!dish) {
                Log.logger(Log.WARN, logId, "Crear Reseña.", "No se ha encontrado el platillo.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            def orders = CustomerOrder.findAllByUserAndStatus(auth, "Finished")
            if (!orders) {
                Log.logger(Log.WARN, logId, "Crear Reseña.", "No se han encontrado ordenes relacionadas al usuario.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            def items = OrderItem.createCriteria().list {
                inList("customerOrder", orders)
                eq("dish", dish)
            }
            if (!items) {
                Log.logger(Log.WARN, logId, "Crear Reseña.", "No se ha encontrado el platillo en las ordenes del usuario.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            
            def review = Review.createCriteria().get {
                eq("user", auth)
                eq("dish", dish)
                ne("status", EntityStatus.INACTIVE)
            }
            
            if (review) {
                Log.logger(Log.WARN, logId, "Crear Reseña.", "Ya existe una reseña del usuario asignada de este platillo.", "data: ${data}")
                return TypeError.existingRegister(logId)
            }
            
            def newReview = new Review([
                user: auth,
                dish: dish,
                comment: data.comment,
                rating: data.rating
            ]).save(flush: true, failOnError: true)
            
            Log.logger(Log.INFO, logId, "Crear Reseña.", "Reseña creada con exito.", "data: ${data}", "Reseña: ${newReview}")
            return [data: [success: true, data: [message: 'Reseña creada', review: mapReview(newReview)]], status: 201]
        } catch (Exception e) {
            Log.logger(Log.ERROR, logId, "Crear Reseña.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
    def statusReview(reviewUuid, status, auth, logId) {
        try {
            Log.logger(Log.INFO, logId, "Actualizar Status de Reseña.", "Llegada al servicio.", "data: { review: ${reviewUuid}, status: ${status} }")
            def review = Review.findByUuid(reviewUuid)
            if (!review) {
                Log.logger(Log.WARN, logId, "Actualizar Status de Reseña.", "No se encontro la reseña.", "data: { review: ${reviewUuid}, status: ${status} }")
                return TypeError.informationNotFound(logId)
            }
            if (status == EntityStatus.INACTIVE) {
                if (review.user.id != auth.id) {
                    Log.logger(Log.WARN, logId, "Actualizar Status de Reseña.", "No se cuenta con permisos para borrar la reseña.", "data: { review: ${reviewUuid}, status: ${status} }")
                    return TypeError.noPermissions(logId)
                }
            }
            if (status in [EntityStatus.DELETED, EntityStatus.ACTIVE]) {
                if (!auth.authorities*.authority.contains('ROLE_ADMIN')) {
                    Log.logger(Log.WARN, logId, "Actualizar Status de Reseña.", "Solo usuarios con 'ROLE_ADMIN' puden activar/desactivar reseñas.", "data: { review: ${reviewUuid}, status: ${status} }")
                    return TypeError.permissionMissing("[ROLE_ADMIN]", logId)
                }
            }
            if (review.status == EntityStatus.INACTIVE) {
                Log.logger(Log.WARN, logId, "Actualizar Status de Reseña.", "La reseña ha sido eliminada.", "data: { review: ${reviewUuid}, status: ${status} }")
                return TypeError.informationNotFound(logId)
            }
            if (review.status == status) {
                Log.logger(Log.WARN, logId, "Actualizar Status de Reseña.", "La reseña ya cuenta con este status.", "data: { review: ${reviewUuid}, status: ${status} }")
                return TypeError.existingRegister(logId)
            }
            review.status = status
            review.save()
            Log.logger(Log.INFO, logId, "Actualizar Status de Reseña.", "Reseña actualizada correctamente.", "data: { review: ${reviewUuid}, status: ${status} }", "review : ${review}")
            return [data: [success: true, data: [message: "Estado de la reseña actualizado.", review: review.uuid]], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Actualizar Status de Reseña.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
    def editReview(dishUuid, data, auth, logId) {
        try {
            Log.logger(Log.INFO, logId, "Editar Reseña.", "Llegada al servicio.", "data: ${data}")
            def dish = Dish.findByUuid(dishUuid)
            if (!dish) {
                Log.logger(Log.WARN, logId, "Editar Reseña.", "No se ha encontrado el platillo.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            def review = Review.findByDishAndUser(dish, auth)
            if (!review) {
                Log.logger(Log.WARN, logId, "Editar Reseña.", "No se encontro la reseña.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            if (review.status != EntityStatus.ACTIVE) {
                Log.logger(Log.WARN, logId, "Editar Reseña.", "La reseña no esta disponible.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            review.comment = (data.comment != null) ? data.comment : review.comment
            review.rating = data.rating as Float
            review.save(flush: true, failOnError: true)
            Log.logger(Log.INFO, logId, "Editar Reseña.", "Reseña editada correctamente.", "data: ${data}")
            return [data: [success: true, data: [message: 'Reseña actualizada', review: mapReview(review)]], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Editar Reseña.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
}
