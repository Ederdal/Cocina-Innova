package restaurante_carlos

import com.ordenaris.restaurant.EntityStatus

class UrlMappings {

    static mappings = {
        
        group "/api",{
            group "/menu", {
                group "/type", {
                    post "/new"(controller: "menu", action: "newType"){
                        constraints {
                            uuid(matches: /^[a-fA-F0-9]{32}$/)
                        }
                    }
                    get "/listMenus"(controller: "menu", action: "listTypes")
                    get "/view"(controller: "menu", action: "paginateTypes")
                    group "/$uuid", {
                        constraints {
                            uuid(matches: /^[a-fA-F0-9]{32}$/)
                        }
                        get "/info"(controller: "menu", action: "typeInfo")  
                        get "/listSubMenu"(controller:"menu", action:"listSubmenusByParent")
                        patch "/edit"(controller: "menu", action: "editType")  
                        patch "/activate"(controller: "menu", action: "editTypeStatus"){  
                            status = EntityStatus.ACTIVE
                        }
                        patch "/deactivate"(controller: "menu", action: "editTypeStatus") {  
                            status = EntityStatus.INACTIVE
                        }
                        delete "/delete"(controller: "menu", action: "editTypeStatus") {
                            status = EntityStatus.DELETED
                        }
                    }
                }
            }
            group "/dish", {  
                post "/new"(controller: "dish", action: "newDish"){
                    constraints {
                        uuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                get "/list"(controller: "dish", action: "listActiveDishes")
                get "/list-all"(controller: "dish", action: "listAllDishes")
                get "/view"(controller: "dish", action: "paginateDishes")
                get "/ranking/rating"(controller: "dish", action: "dishRankingByRating")
                get "/ranking/topselling"(controller: "dish", action: "topSellingDishes")
                get "/chart-top-dishes"(controller: "dish", action: "topDishesChart")
                group "/$uuid", {
                    
                    get "/info"(controller: "dish", action: "dishInfo"){
                        status = EntityStatus.ACTIVE
                    }  
                    get "/clone"(controller:"dish", action:"dishInfo"){
                        status = EntityStatus.DELETED
                    }
                    post "/clone"(controller:"dish", action:"newDish"){
                    }
                    patch "/edit"(controller: "dish", action: "editDish"){
                    } 
                    patch "/activate"(controller: "dish", action: "editDishStatus"){  
                        status = EntityStatus.ACTIVE
                    }
                    patch "/deactivate"(controller: "dish", action: "editDishStatus") {  
                        status = EntityStatus.INACTIVE
                    }
                    delete "/delete"(controller: "dish", action: "editDishStatus") {
                        status = EntityStatus.DELETED
                    }
                    
                    post "/upload-image"(controller: "dish", action: "uploadDishImage"){
                    }
                    patch "/reload-image"(controller: "dish", action: "reloadDishImage"){
                    }
                    delete "/image"(controller: "dish", action: "deleteDishImage"){
                    }
                    get "/image"(controller: "dish", action: "getDishImage"){
                    }
                    patch "/increment-available"(controller: "dish", action: "incrementAvailableDishes"){
                    }
                }
            }
            

            group "/user", {  
                post "/register"(controller: "user", action: "register")
                get "/paginate"(controller: "user", action: "paginateUsers")
                get "/info"(controller: "user", action: "getUserInfo")
                get "/profile-picture"(controller: "user", action: "getProfilePicture") 
                post "/profile-picture"(controller: "user", action: "changeProfilePicture")
                patch "/change-crd/$status"(controller: "user", action: "updateChangeCrdRequest"){
                    constraints {
                        status inList:[
                            "request",
                            "cancel"
                        ]
                    }
                }
                group "/$uuid", {
                    constraints{
                        uuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                    patch "/change-status/$status"(controller: "user", action: "changeStatus"){
                        constraints {
                            status inList:[
                                "active",
                                "deactivate",
                                "block",
                                "unlock"
                            ]
                        }
                    }
                    patch "/change-crd"(controller: "user", action: "changeUserCrd")
                    get "/profile-picture"(controller: "user", action: "getUserProfilePicture")
                    post "/profile-picture"(controller: "user", action: "changeUserProfilePicture")
                }
            }

            group "/role", {

                get "/list-all"(controller: "role", action: "listAllRoles")
                post "/create"(controller: "role", action: "createNewRole")
                group "/$uuid", {
                    constraints{
                        uuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                    get "/info"(controller: "role", action: "getRoleInfo")
                    delete "/delete"(controller: "role", action: "deleteRole")
                    patch "/change-authority"(controller: "role", action: "changeAuthority")
                }

            }

            group "/user-role", {
                get "/get-roles/$uuidUser"(controller: "userRole", action: "getRolesByUser"){
                    constraints{
                        uuidUser(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                post "/assign-role/$uuidUser/$uuidRole"(controller: "userRole", action: "assignRole"){
                    constraints{
                        uuidUser(matches: /^[a-fA-F0-9]{32}/)
                        uuidRole(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                delete "/remove-role/$uuidUser/$uuidRole"(controller: "userRole", action: "removeRole"){
                    constraints{
                        uuidUser(matches: /^[a-fA-F0-9]{32}/)
                        uuidRole(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                patch "/change-role/$uuidUser/$uuidRole/$uuidNewRole"(controller: "userRole", action: "changeRole"){
                    constraints{
                        uuidUser(matches: /^[a-fA-F0-9]{32}/)
                        uuidRole(matches: /^[a-fA-F0-9]{32}/)
                        uuidNewRole(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
            }

            group "/schedule", {
                get "/list-all-schedules"(controller: "schedule", action: "listAllSchedules")
                get "/is-any-chef-available"(controller: "schedule", action: "isAnyChefAvailable")
                group "/user/$uuidUser", {
                    constraints{
                        uuidUser(matches: /^[a-fA-F0-9]{32}/)
                    }
                    get "/get-user-schedule"(controller: "schedule", action: "getScheduleInfo")
                    post "/create-schedule"(controller: "schedule", action: "createUserSchedule")
                    patch "/change-working-hours"(controller: "schedule", action: "changeWorkingHours")
                    patch "/change-availability/$status"(controller: "schedule", action: "changeAvailability"){
                        constraints {
                            status inList:[
                                "working",
                                "not-working"
                            ]
                        }
                    }
                    delete "/delete-schedule"(controller: "schedule", action: "deleteSchedule")
                }
            }

            group "/settings",{
                put"/update-information"(controller: "settings", action: "updateInfoDB")
            }

            group "/sale", {
                get "/debtors/all"(controller: "sale", action: "listDebtors")
                get "/debtors/$userUuid/details"(controller: "sale", action: "getDetailsByUser"){
                    constraints {
                        userUuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                post "/orders/pay-specific"(controller: "sale", action: "paySingleSale")
                post "/orders/pay-dish"(controller: "sale", action: "paySingleDish")
                post "/orders/pay-multiple-orders"(controller: "sale", action: "payMultipleSalesForUser")
                post "/orders/pay-all-user"(controller: "sale", action: "payAllSalesForUser")
                get "/orders/paid"(controller: "sale", action: "getSalesPaid")
                get "/date"(controller: "sale", action: "getUserSalesByDateRange")
                post "/user-expenses-chart"(controller: "sale", action: "getUserSpendingChart")
                get "/consume-chart"(controller: "sale", action: "getConsumeChart")
                get "/pending"(controller: "sale", action: "getSalesByUser") {
                    typeSale = "Pending"
                }
                get "/paid"(controller: "sale", action: "getSalesByUser") {
                    typeSale = "Paid"
                }
                get "/all"(controller: "sale", action: "getSalesByUser") {
                    typeSale = "all"
                }
                get "/$saleUuid"(controller: "sale", action: "getOneSaleInfo"){
                    constraints {
                        saleUuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
            }
            group "/order", {
                get "/listOrderByChef"(controller: "ordersModule", action: "listOrderByChef")
                post "/newOrder"(controller: "ordersModule", action: "newOrder")
                get "/listOrders"(controller: "ordersModule", action: "listOrders")
                get "/myOrders"(controller: "ordersModule", action: "listOrdersByUser")
                get "/rejections"(controller: "ordersModule", action: "listRejections")
                group "/$uuidOrder", {
                    group "/edit/$uuidItem",{
                        patch "/dish"(controller: "ordersModule", action: "editOrder")
                        patch "/reject"(controller: "ordersModule", action: "rejectDish")
                        constraints {
                            uuidItem(matches: /^[a-fA-F0-9]{32}/)
                        }
                    }
                    patch "/addDish"(controller: "ordersModule", action: "addDishOrder")
                    patch "/cancel"(controller: "ordersModule", action: "editOrderStatus") {status = "Cancelled"}
                    patch "/prepare"(controller: "ordersModule", action: "editOrderStatus") {status = "Preparing"}
                    patch "/finish"(controller: "ordersModule", action: "editOrderStatus") {status = "Finished"}
                    patch "/queue"(controller: "ordersModule", action: "editOrderStatus") {status = "Queue"}
                    group "/rejection/$rejectionUuid", {
                        get "/info"(controller: "ordersModule", action: "rejectionInfo")
                        patch "/approve"(controller: "ordersModule", action: "approveRejection")
                        patch "/cancel"(controller: "ordersModule", action: "cancelRejection")
                    }
                    constraints {
                        uuidOrder(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
            }
            group "/shoppingCart", {
                get "/list"(controller: "shoppingCart", action: "listOrderShoppingCart")
                get "/byUser" (controller: "shoppingCart", action: "getCartByUser")
                post "/new"(controller: "shoppingCart", action: "newOrderShoppingCart")
                group "/$uuidSC", {
                    post "/addItem"(controller: "shoppingCart", action: "addItemShoppingCart")
                    delete "/deleteItem/$uuidItem"(controller: "shoppingCart", action: "deleteItemShoppingCart")
                    put "/addNumberDish/$uuidItem"(controller:"shoppingCart", action:"addNumberDish")
                    put "/restNumberDish/$uuidItem"(controller:"shoppingCart", action:"restNumberDish")
                    patch "/finish"(controller: "shoppingCart", action: "editStatusShoppingCart"){
                        status = "Finished"
                    }
                    delete "/delete"(controller: "shoppingCart", action: "editStatusShoppingCart"){
                        status = "Delete"
                    }
                    constraints {
                        uuidSC(matches: /^[a-fA-F0-9]{32}/)
                        uuidItem(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
            }
            group "/review", {
                get "/list"(controller: "review", action: "listReviews")
                get "/count-total"(controller: "review", action: "countTotalReviews")
                get "/stats/$dishUuid"(controller: "review", action: "statisticsDish"){
                    constraints {
                        dishUuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                post "/new"(controller: "review", action: "createReview")
                group "/$reviewUuid", {
                    delete "/delete"(controller: "review", action: "statusReview"){ status = 2 }
                    patch "/deactivate"(controller: "review", action: "statusReview"){ status = 0 }
                    patch "/activate"(controller: "review", action: "statusReview"){ status = 1 }
                    constraints {
                        reviewUuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
                patch "/$dishUuid/edit"(controller: "review", action: "editReview"){
                    constraints {
                        dishUuid(matches: /^[a-fA-F0-9]{32}/)
                    }
                }
            }
        }
        get "/uploads/dishes/$fileName"(controller: "dish", action: "publicDishImage") {
            constraints {
                fileName(matches: /^[a-zA-Z0-9_\-]+\.(jpg|jpeg|png|gif|webp)$/)
            }
        }
        "/"(controller: 'application', action:'index')
        "401"(controller: "application", action: "unauthorized")
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
